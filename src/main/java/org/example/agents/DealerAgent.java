package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import org.example.MainUI.UILogger;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DealerAgent — Phase 2
 *
 * Negotiation is now fully broker-routed.
 * Inbound ontologies:
 *   BROKER_INVITE      (REQUEST)  — first offer relayed by broker; "sessionId;buyerName;car;offer"
 *   BROKER_RELAY_OFFER (PROPOSE)  — subsequent buyer counter relayed by broker; same schema
 *   CYCLE_UPDATE / START_CYCLE    — price adjustment from SpaceControl
 *   PRICE_ADJUSTMENT              — manual override from GUI
 *
 * Outbound ontologies:
 *   DEALER_COUNTER (REJECT_PROPOSAL) → broker
 *   DEALER_ACCEPT  (ACCEPT_PROPOSAL) → broker
 *
 * Extension 1 foundation: per-session state is isolated via sessionId so one
 * dealer can safely handle multiple concurrent buyers.
 */
public class DealerAgent extends Agent {

    private String car;
    private int    minPrice;      // reserve price
    private int    retailPrice;
    private int    currentTargetPrice;
    private UILogger logger;
    private int    negotiationCount = 0;
    private NegotiationConfig config = NegotiationConfig.defaults();
    private int    stockCount;
    private int    manualTargetPrice = -1;
    private NegotiationConfig.Strategy activeStrategy;
    /** All session IDs currently being negotiated. Used to notify broker on stock-out. */
    private final Set<String> activeSessions = new LinkedHashSet<>();

    @Override
    protected void setup() {
        Object[] args = getArguments();
        car         = (String) args[0];
        retailPrice = Integer.parseInt((String) args[1]);
        stockCount  = Integer.parseInt((String) args[2]);
        logger      = (UILogger) args[3];
        if (args.length > 4 && args[4] instanceof NegotiationConfig) {
            config = (NegotiationConfig) args[4];
        }
        minPrice           = (int)(retailPrice * config.getDealerReservePercent());
        currentTargetPrice = retailPrice;
        activeStrategy     = config.getStrategy();

        log("STATUS: Listed " + car + " @ RM" + retailPrice + " | Reserve: RM" + minPrice
                + " | Stock: " + stockCount + " | Strategy: " + config.getStrategy() + strategySwitchText());

        // ── Register with broker and SpaceControl ─────────────────────────────
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                // Register inventory with broker
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                inform.addReceiver(new AID("broker", AID.ISLOCALNAME));
                inform.setContent(car + ";" + retailPrice + ";" + stockCount + ";" + minPrice);
                send(inform);

                // Register with SpaceControl for CYCLE_UPDATE broadcasts
                ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
                reg.setOntology("REGISTER");
                reg.addReceiver(new AID("space", AID.ISLOCALNAME));
                send(reg);
            }
        });

        // ── Main message loop ─────────────────────────────────────────────────
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }

                String ont = msg.getOntology() == null ? "" : msg.getOntology();

                if ("CYCLE_UPDATE".equals(ont) || "START_CYCLE".equals(ont)) {
                    handleCycleUpdate(msg);

                } else if ("PRICE_ADJUSTMENT".equals(ont)) {
                    handleManualPriceAdjustment(msg);

                } else if ("BROKER_INVITE".equals(ont)) {
                    // Content: "sessionId;buyerName;carModel;offer" — first contact for this session
                    String sid = msg.getContent().split(";")[0];
                    activeSessions.add(sid);
                    handleBrokerOffer(msg);
                } else if ("BROKER_RELAY_OFFER".equals(ont)) {
                    // Content: "sessionId;buyerName;carModel;offer" — subsequent buyer counter
                    handleBrokerOffer(msg);
                }
                // ignore DEREGISTER/ACTION_COMPLETED leaks from other agents
            }
        });
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleCycleUpdate(ACLMessage msg) {
        int currentCycle = Integer.parseInt(msg.getContent());
        int t = Math.min(currentCycle, config.getDeadlineCycles());

        NegotiationConfig.Strategy effective = config.getEffectiveStrategy(t);
        if (effective != activeStrategy) {
            activeStrategy = effective;
            log("STATUS: Strategy shifted to " + activeStrategy + " at cycle " + t);
        }
        double concessionFactor = Math.pow((double) t / config.getDeadlineCycles(), config.betaForCycle(t));
        int cycleTarget = (int)(retailPrice - ((retailPrice - minPrice) * concessionFactor));
        currentTargetPrice = manualTargetPrice >= 0
                ? Math.max(minPrice, manualTargetPrice)
                : Math.max(minPrice, (int)(cycleTarget * config.getManualDealerTargetPercent()));
        log("Price updated to RM" + currentTargetPrice + " (cycle " + t + ")");
    }

    private void handleManualPriceAdjustment(ACLMessage msg) {
        try {
            int adjusted = Integer.parseInt(msg.getContent());
            manualTargetPrice  = Math.max(minPrice, adjusted);
            currentTargetPrice = manualTargetPrice;
            log("STATUS: Manual target adjusted to RM" + currentTargetPrice);
        } catch (NumberFormatException e) {
            log("STATUS: Ignored invalid manual price: " + msg.getContent());
        }
    }

    private void handleBrokerOffer(ACLMessage msg) {
        String[] p    = msg.getContent().split(";");
        String sessionId  = p[0];

        if (stockCount <= 0) {
            // Already sold out, reject immediately (zombie window)
            activeSessions.remove(sessionId);
            ACLMessage soldOut = new ACLMessage(ACLMessage.INFORM);
            soldOut.addReceiver(new AID("broker", AID.ISLOCALNAME));
            soldOut.setOntology("DEALER_SOLD_OUT");
            soldOut.setContent(sessionId);
            send(soldOut);
            return;
        }

        // p[1] = buyerName, p[2] = carModel — available for logging / Extension 1 per-session state
        int buyerOffer    = Integer.parseInt(p[3]);
        negotiationCount++;

        log("OFFER #" + negotiationCount + ": [" + sessionId + "] Buyer offered RM" + buyerOffer
                + " (target=RM" + currentTargetPrice + ")");

        if (buyerOffer >= currentTargetPrice) {
            // Accept — decrement stock and remove this session from active set
            stockCount--;
            activeSessions.remove(sessionId);

            ACLMessage accept = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
            accept.addReceiver(new AID("broker", AID.ISLOCALNAME));
            accept.setOntology("DEALER_ACCEPT");
            accept.setContent(sessionId + ";" + buyerOffer);

            // Snapshot remaining sessions at the exact moment of stock-out
            final boolean isStockOutTrigger = (stockCount == 0);
            final String pendingSessionsCsv = (isStockOutTrigger && !activeSessions.isEmpty())
                    ? activeSessions.stream().collect(Collectors.joining(","))
                    : "";

            if (isStockOutTrigger) {
                // Clear active sessions so we don't accidentally process them further
                activeSessions.clear();
            }

            addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 600) {
                @Override
                protected void onWake() {
                    send(accept);
                    notifySpace();
                    if (isStockOutTrigger) {
                        // Notify broker of all sessions that can no longer be served
                        if (!pendingSessionsCsv.isEmpty()) {
                            ACLMessage soldOut = new ACLMessage(ACLMessage.INFORM);
                            soldOut.addReceiver(new AID("broker", AID.ISLOCALNAME));
                            soldOut.setOntology("DEALER_SOLD_OUT");
                            soldOut.setContent(pendingSessionsCsv);
                            send(soldOut);
                            log("STATUS: Out of stock. Notifying broker of " +
                                    pendingSessionsCsv.split(",").length + " pending session(s).");
                        } else {
                            log("STATUS: Out of stock. No pending sessions.");
                        }
                        doDelete();
                    }
                }
            });
            log("DEAL CLOSED: [" + sessionId + "] RM" + buyerOffer + " | Stock: " + stockCount);
        } else {
            // Counter-offer
            ACLMessage counter = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
            counter.addReceiver(new AID("broker", AID.ISLOCALNAME));
            counter.setOntology("DEALER_COUNTER");
            counter.setContent(sessionId + ";" + currentTargetPrice);
            
            addBehaviour(new jade.core.behaviours.WakerBehaviour(this, 600) {
                @Override
                protected void onWake() {
                    send(counter);
                    notifySpace();
                }
            });
            log("COUNTER: [" + sessionId + "] RM" + currentTargetPrice);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void notifySpace() {
        ACLMessage action = new ACLMessage(ACLMessage.INFORM);
        action.setOntology("ACTION_COMPLETED");
        action.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(action);
    }

    @Override
    protected void takeDown() {
        ACLMessage dereg = new ACLMessage(ACLMessage.INFORM);
        dereg.setOntology("DEREGISTER");
        dereg.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(dereg);
        log("Terminating");
    }

    private void log(String m) {
        if (logger != null) logger.log(getLocalName() + ": " + m);
    }

    private String strategySwitchText() {
        if (config.getStrategySwitchCycle() <= 0 || config.getSwitchStrategy() == config.getStrategy()) return "";
        return " → " + config.getSwitchStrategy() + " at cycle " + config.getStrategySwitchCycle();
    }
}

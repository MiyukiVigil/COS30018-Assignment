package org.example.agents;

import java.util.ArrayList;
import java.util.List;
import org.example.MainUI.UILogger;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * BuyerAgent — Phase 3
 *
 * Negotiation is now fully broker-routed. The buyer NEVER contacts a dealer directly.
 *
 * Flow:
 *  1. Send BUYER_SEARCH (REQUEST) to broker with sessionId + carModel
 *  2. Receive BROKER_SHORTLIST (PROPOSE) — parse dealers
 *  3. Pick next unaffordable-skipped dealer; send BUYER_SHORTLIST (PROPOSE) to broker
 *     (broker creates session, charges fixed fee, invites dealer)
 *  4. Receive BROKER_RELAY_COUNTER (REJECT_PROPOSAL) — send BUYER_COUNTER (PROPOSE) to broker
 *  5. Receive BROKER_RELAY_ACCEPT (ACCEPT_PROPOSAL) — deal done
 *  6. If max rounds reached → BUYER_WALKAWAY (FAILURE) to broker → try next dealer
 *
 * Session IDs: "{buyerLocalName}-{carModel}-{attemptIndex}"
 * Each dealer attempt is a new session (new fixed fee).
 */
public class BuyerAgent extends Agent {

    // ── Config ────────────────────────────────────────────────────────────────
    private String desiredCar;
    private int    maxBudget;
    private UILogger logger;
    private NegotiationConfig config = NegotiationConfig.defaults();
    private UtilityPreferences preferences = AppConfig.defaults().utilityPreferences();
    private boolean negotiationStarted = true;  // false = wait for manual start signal
    private boolean isManualStrategy = false;
    private static final int MAX_DEALER_NEGOTIATIONS = 3;

    // ── Negotiation state ─────────────────────────────────────────────────────
    private final List<DealerOption> dealers = new ArrayList<>();
    private int    currentDealerIdx   = 0;
    private int    dealerAttemptIndex = 0;  // increments per dealer attempt → unique sessionIds
    private String activeSessionId    = null;

    private int    negotiationRound       = 0;
    private int    startCycle             = -1;
    private int    searchRetries          = 0;
    private boolean dealFound             = false;

    private int    initialOffer;
    private int    currentWillingOffer;
    private NegotiationConfig.Strategy activeStrategy;
    private NegotiationTerms currentTerms;

    // ── Inner types ───────────────────────────────────────────────────────────
    private static class DealerOption {
        String name;
        int listedPrice;
        int reservePrice;
        DealerOption(String n, int listed, int reserve) {
            name = n; listedPrice = listed; reservePrice = reserve;
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void setup() {
        Object[] args = getArguments();
        desiredCar = (String) args[0];
        maxBudget  = Integer.parseInt((String) args[1]);
        logger     = (UILogger) args[2];
        if (args.length > 3 && args[3] instanceof NegotiationConfig) config = (NegotiationConfig) args[3];
        if (args.length > 4 && args[4] instanceof Boolean) negotiationStarted = !((Boolean) args[4]);
        if (args.length > 5 && args[5] instanceof Boolean) isManualStrategy = (Boolean) args[5];

        initialOffer       = (int)(maxBudget * config.getBuyerStartPercent());
        currentWillingOffer = initialOffer;
        currentTerms = buyerTermsForPrice(currentWillingOffer);
        activeStrategy     = config.getStrategy();

        log("STATUS: " + (negotiationStarted ? "Searching" : "Waiting to start")
                + " for " + desiredCar + " (Budget: RM" + maxBudget
                + ", Strategy: " + config.getStrategy() + strategySwitchText() + ")");

        // Kick off search if auto-start
        addBehaviour(new OneShotBehaviour() {
            @Override public void action() {
                if (negotiationStarted) startNegotiationSession();
            }
        });

        // Main message loop
        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }

                String ont = msg.getOntology() == null ? "" : msg.getOntology();

                switch (ont) {
                    case "START_NEGOTIATION":
                        if (!negotiationStarted) startNegotiationSession();
                        else log("STATUS: Start ignored — negotiation already running.");
                        break;

                    case "STOP_NEGOTIATION":
                        log("STATUS: Negotiation stopped by user.");
                        sendWalkaway(activeSessionId, "USER_STOPPED");
                        triggerMarketAction();
                        doDelete();
                        break;

                    case "CYCLE_UPDATE":
                        if (negotiationStarted) handleCycleUpdate(msg);
                        break;

                    case "BROKER_SHORTLIST":
                        if (negotiationStarted) handleBrokerShortlist(msg);
                        break;

                    case "BROKER_RELAY_COUNTER":
                        // Broker relays dealer's counter-offer to us
                        if (negotiationStarted) handleBrokerRelayCounter(msg);
                        break;

                    case "BROKER_RELAY_ACCEPT":
                        // Broker confirms deal
                        if (negotiationStarted) handleBrokerRelayAccept(msg);
                        break;

                    case "BROKER_RELAY_SOLD_OUT":
                        // Broker tells us the dealer ran out of stock — move to next dealer
                        if (negotiationStarted) handleBrokerRelaySoldOut(msg);
                        break;

                    case "BROKER_SESSION_REJECTED":
                        if (negotiationStarted) handleBrokerSessionRejected(msg);
                        break;

                    case "MANUAL_ACTION":
                        handleManualAction(msg);
                        break;

                    default:
                        break;
                }
            }
        });
    }

    // ── Session management ────────────────────────────────────────────────────

    private void startNegotiationSession() {
        negotiationStarted = true;
        log("STATUS: Negotiation started for " + desiredCar + ".");

        // Register with SpaceControl
        ACLMessage reg = new ACLMessage(ACLMessage.INFORM);
        reg.setOntology("REGISTER");
        reg.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(reg);

        searchBroker();
    }

    /** Send BUYER_SEARCH to broker. Uses a placeholder sessionId for the search phase. */
    public void searchBroker() {
        // Session ID for the search itself (not a negotiation session yet)
        String searchId = getLocalName() + "-" + desiredCar + "-search";
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(new AID("broker", AID.ISLOCALNAME));
        req.setOntology("BUYER_SEARCH");
        req.setContent(searchId + ";" + desiredCar);
        send(req);
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleCycleUpdate(ACLMessage msg) {
        int currentCycle = Integer.parseInt(msg.getContent());
        if (startCycle == -1) startCycle = currentCycle;

        int t = Math.min(currentCycle - startCycle, config.getDeadlineCycles());
        NegotiationConfig.Strategy effective = config.getEffectiveStrategy(t);
        if (effective != activeStrategy) {
            activeStrategy = effective;
            log("STATUS: Strategy shifted to " + activeStrategy + " at local cycle " + t);
        }
        double concession = Math.pow((double) t / config.getDeadlineCycles(), config.betaForCycle(t));
        currentWillingOffer = (int)(initialOffer + ((maxBudget - initialOffer) * concession));
        if (currentWillingOffer > maxBudget) currentWillingOffer = maxBudget;
        currentTerms = buyerTermsForPrice(currentWillingOffer);
        log("Willing to pay RM" + currentWillingOffer + " for " + desiredCar
                + termsText(currentTerms));
    }

    /**
     * BROKER_SHORTLIST: "searchSessionId;dealer1:price1:reserve1,dealer2:..."  or  "...;NONE"
     * Parse shortlist, verify affordability, send BUYER_SHORTLIST to broker.
     */
    private void handleBrokerShortlist(ACLMessage msg) {
        // Content: "{searchId};{dealerCsv}"  where dealerCsv may be "NONE"
        String content = msg.getContent();
        int    sep     = content.indexOf(';');
        String dealerCsv = sep >= 0 ? content.substring(sep + 1) : content;

        if ("NONE".equals(dealerCsv)) {
            searchRetries++;
            if (searchRetries > config.getMaxSearchRetries()) {
                log("STATUS: No matching car after " + config.getMaxSearchRetries() + " retries. Ending.");
                sendNoDealReport("NO_MATCHING_CAR");
                triggerMarketAction();
                doDelete();
            } else {
                log("STATUS: No dealers for " + desiredCar + ". Retry " + searchRetries
                        + "/" + config.getMaxSearchRetries());
                addBehaviour(new WakerBehaviour(this, 1500) {
                    @Override protected void onWake() { searchBroker(); }
                });
            }
            return;
        }

        dealers.clear();
        for (String entry : dealerCsv.split(",")) {
            if (entry.isEmpty()) continue;
            String[] p = entry.split(":");
            int listed  = p.length > 1 ? Integer.parseInt(p[1]) : maxBudget;
            int reserve = p.length > 2 ? Integer.parseInt(p[2]) : listed;
            dealers.add(new DealerOption(p[0], listed, reserve));
        }
        dealers.sort((a, b) -> Integer.compare(a.listedPrice, b.listedPrice));
        while (dealers.size() > MAX_DEALER_NEGOTIATIONS) {
            dealers.remove(dealers.size() - 1);
        }

        // Check at least one dealer is affordable
        boolean anyAffordable = dealers.stream().anyMatch(d -> d.reservePrice <= maxBudget);
        if (!anyAffordable) {
            log("STATUS: All dealers' reserve prices exceed budget RM" + maxBudget + ". Ending.");
            sendNoDealReport("BUDGET_TOO_LOW");
            triggerMarketAction();
            doDelete();
            return;
        }

        searchRetries = 0;
        currentDealerIdx = 0;
        dealerAttemptIndex = 0;
        
        if (isManualStrategy) {
            log("STATUS: Manual Shortlist Phase. Waiting for user input...");
            StringBuilder sb = new StringBuilder();
            for (DealerOption d : dealers) {
                if (d.reservePrice <= maxBudget) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(d.name).append(":").append(d.listedPrice).append(":").append(d.reservePrice);
                }
            }
            log("[MANUAL_PROMPT] SHORTLIST:" + sb.toString());
            return;
        }

        log("STATUS: Shortlist received — " + dealers.size() + " dealer(s). Starting negotiations...");
        submitShortlistToBroker();
    }

    /**
     * Pick the current dealer (skip if unaffordable), generate a new sessionId,
     * and send BUYER_SHORTLIST to broker.
     */
    private void submitShortlistToBroker() {
        // Skip dealers whose reserve price exceeds budget
        while (currentDealerIdx < dealers.size()
                && dealers.get(currentDealerIdx).reservePrice > maxBudget) {
            log("STATUS: Skipping " + dealers.get(currentDealerIdx).name
                    + " (reserve RM" + dealers.get(currentDealerIdx).reservePrice + " > budget)");
            currentDealerIdx++;
        }

        if (dealerAttemptIndex >= MAX_DEALER_NEGOTIATIONS || currentDealerIdx >= dealers.size()) {
            if (!dealFound) {
                log("STATUS: Dealer negotiation limit reached. No deal reached.");
                if (dealerAttemptIndex == 0) {
                    sendNoDealReport("MAX_ROUNDS_REACHED");
                }
                triggerMarketAction();
                doDelete();
            }
            return;
        }

        DealerOption dealer = dealers.get(currentDealerIdx);
        negotiationRound = 0;
        activeSessionId  = getLocalName() + "-" + desiredCar + "-" + dealerAttemptIndex;

        NegotiationTerms firstTerms = buyerTermsForPrice(currentWillingOffer);

        // Content: "sessionId;dealerName;terms;buyerReserve;carModel"
        ACLMessage shortlist = new ACLMessage(ACLMessage.PROPOSE);
        shortlist.addReceiver(new AID("broker", AID.ISLOCALNAME));
        shortlist.setOntology("BUYER_SHORTLIST");
        shortlist.setContent(activeSessionId + ";" + dealer.name + ";"
                + firstTerms.toPayload() + ";" + maxBudget + ";" + desiredCar);
        
        addBehaviour(new WakerBehaviour(this, 400) {
            @Override
            protected void onWake() {
                send(shortlist);
            }
        });

        log("NEGOTIATION: Starting with " + dealer.name + " [session=" + activeSessionId
                + "] @ RM" + currentWillingOffer + termsText(firstTerms));
    }

    /**
     * BROKER_RELAY_COUNTER: "sessionId;dealerName;counterPrice"
     * The broker has relayed the dealer's counter-offer to us.
     */
    private void handleBrokerRelayCounter(ACLMessage msg) {
        String[] p   = msg.getContent().split(";");
        String sid   = p[0];
        // p[1] = dealerName
        NegotiationTerms counterTerms = NegotiationTerms.fromPayload(p[2]);
        int counter  = counterTerms.getPrice();
        negotiationRound++;

        log("OFFER: [" + sid + "] Dealer counter-offered RM" + counter
                + termsText(counterTerms) + " (Round " + negotiationRound + ")");

        if (isManualStrategy) {
            log("[MANUAL_PROMPT] COUNTER:" + p[1] + ":" + counter);
            return;
        }

        if (counter <= currentWillingOffer || acceptableUtility(counterTerms)) {
            // Deal! Send agreeing counter back via broker (broker will forward to dealer)
            sendBuyerCounter(sid, counterTerms);
            log("AGREED: Counter RM" + counter + " is within budget. Sending final offer.");
            // Note: the actual deal confirmation comes from DEALER_ACCEPT → BROKER_RELAY_ACCEPT
        } else if (negotiationRound < config.getMaxRoundsPerDealer()) {
            // Acceleration if stuck
            if (negotiationRound >= config.getStuckRoundsBeforeAcceleration()) {
                currentWillingOffer = Math.min(maxBudget,
                        currentWillingOffer + Math.max(1, (maxBudget - currentWillingOffer) / 2));
                currentTerms = buyerTermsForPrice(currentWillingOffer);
                log("STATUS: Negotiation dragging. Accelerated offer to RM" + currentWillingOffer);
            }
            sendBuyerCounter(sid, buyerTermsForPrice(currentWillingOffer));
            log("COUNTER: Sent RM" + currentWillingOffer + termsText(currentTerms) + " to broker.");
            triggerMarketAction();
        } else {
            // Walk away from this dealer, try next
            log("STATUS: Max rounds reached on " + sid + ". Moving to next dealer...");
            sendWalkaway(sid, "MAX_ROUNDS_REACHED");
            currentDealerIdx++;
            dealerAttemptIndex++;
            submitShortlistToBroker();
        }
    }

    /**
     * BROKER_RELAY_ACCEPT: "sessionId;dealerName;agreedPrice"
     * The deal is confirmed by the broker (dealer already accepted).
     */
    private void handleBrokerRelayAccept(ACLMessage msg) {
        String[] p   = msg.getContent().split(";");
        // p[0]=sessionId, p[1]=dealerName
        NegotiationTerms agreedTerms = NegotiationTerms.fromPayload(p[2]);
        int agreedPrice = agreedTerms.getPrice();
        log("SUCCESS! Purchased " + desiredCar + " for RM" + agreedPrice
                + termsText(agreedTerms) + " from " + p[1] + " [session=" + p[0] + "]");
        dealFound = true;
        triggerMarketAction();
        doDelete();
    }

    /**
     * BROKER_RELAY_SOLD_OUT: "sessionId;dealerName"
     * The dealer went out of stock mid-negotiation.
     * Advance to the next dealer in the shortlist immediately.
     */
    private void handleBrokerRelaySoldOut(ACLMessage msg) {
        String[] p      = msg.getContent().split(";", 2);
        String sessionId = p[0];
        String dealer   = p.length > 1 ? p[1] : "?";
        log("STATUS: Dealer " + dealer + " sold out [session=" + sessionId + "]. Trying next dealer...");
        currentDealerIdx++;
        dealerAttemptIndex++;
        submitShortlistToBroker();
    }

    // ── Outbound helpers ──────────────────────────────────────────────────────

    private void handleBrokerSessionRejected(ACLMessage msg) {
        String[] p = msg.getContent().split(";", 2);
        String sid = p.length > 0 ? p[0] : activeSessionId;
        String reason = p.length > 1 ? p[1] : "UNKNOWN";
        log("STATUS: Broker closed/rejected session " + sid + " (" + reason + ").");
        if ("TIMEOUT".equals(reason)) {
            triggerMarketAction();
            doDelete();
            return;
        }
        currentDealerIdx++;
        dealerAttemptIndex++;
        submitShortlistToBroker();
    }

    /** Send BUYER_COUNTER (PROPOSE / BUYER_COUNTER) to broker */
    private void sendBuyerCounter(String sessionId, NegotiationTerms terms) {
        ACLMessage counter = new ACLMessage(ACLMessage.PROPOSE);
        counter.addReceiver(new AID("broker", AID.ISLOCALNAME));
        counter.setOntology("BUYER_COUNTER");
        counter.setContent(sessionId + ";" + terms.toPayload());
        
        addBehaviour(new WakerBehaviour(this, 600) {
            @Override
            protected void onWake() {
                send(counter);
            }
        });
    }

    /** Send BUYER_WALKAWAY (FAILURE / BUYER_WALKAWAY) to broker */
    private void sendWalkaway(String sessionId, String reason) {
        if (sessionId == null) {
            sendNoDealReport(reason);
            return;
        }
        ACLMessage walkaway = new ACLMessage(ACLMessage.FAILURE);
        walkaway.addReceiver(new AID("broker", AID.ISLOCALNAME));
        walkaway.setOntology("BUYER_WALKAWAY");
        walkaway.setContent(sessionId + ";" + reason);
        send(walkaway);
    }

    private void sendNoDealReport(String reason) {
        String reportId = getLocalName() + "-" + desiredCar + "-pre-session-" + reason;
        ACLMessage walkaway = new ACLMessage(ACLMessage.FAILURE);
        walkaway.addReceiver(new AID("broker", AID.ISLOCALNAME));
        walkaway.setOntology("BUYER_WALKAWAY");
        walkaway.setContent(reportId + ";" + reason + ";" + desiredCar + ";" + maxBudget);
        send(walkaway);
    }

    private void triggerMarketAction() {
        ACLMessage action = new ACLMessage(ACLMessage.INFORM);
        action.setOntology("ACTION_COMPLETED");
        action.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(action);
    }

    private void handleManualAction(ACLMessage msg) {
        String content = msg.getContent();
        if (content == null || content.isEmpty()) return;
        
        String[] parts = content.split(";");
        String action = parts[0];
        
        if ("SHORTLIST".equals(action) && parts.length >= 3) {
            String dealerName = parts[1];
            int firstOffer = Integer.parseInt(parts[2]);
            if (dealerName == null || dealerName.trim().isEmpty() || firstOffer <= 0) {
                log("MANUAL: Select a dealer and enter a positive first offer.");
                return;
            }
            
            activeSessionId = getLocalName() + "-" + desiredCar + "-" + dealerAttemptIndex;
            ACLMessage prop = new ACLMessage(ACLMessage.PROPOSE);
            prop.addReceiver(new AID("broker", AID.ISLOCALNAME));
            prop.setOntology("BUYER_SHORTLIST");
            prop.setContent(activeSessionId + ";" + dealerName + ";" + buyerTermsForPrice(firstOffer).toPayload()
                    + ";" + maxBudget + ";" + desiredCar);
            send(prop);
            
            log("MANUAL: Sent first offer RM" + firstOffer + " to " + dealerName);
            
        } else if ("COUNTER".equals(action) && parts.length >= 2) {
            int price = Integer.parseInt(parts[1]);
            if (activeSessionId == null || price <= 0) {
                log("MANUAL: Counter requires an active session and a positive price.");
                return;
            }
            ACLMessage counterMsg = new ACLMessage(ACLMessage.PROPOSE);
            counterMsg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            counterMsg.setOntology("BUYER_COUNTER");
            counterMsg.setContent(activeSessionId + ";" + buyerTermsForPrice(price).toPayload());
            send(counterMsg);
            log("MANUAL: Sent counter offer RM" + price);
            
        } else if ("ACCEPT".equals(action) && parts.length >= 2) {
            int price = Integer.parseInt(parts[1]);
            if (activeSessionId == null || price <= 0) {
                log("MANUAL: Accept requires an active session and a positive price.");
                return;
            }
            ACLMessage counterMsg = new ACLMessage(ACLMessage.PROPOSE);
            counterMsg.addReceiver(new AID("broker", AID.ISLOCALNAME));
            counterMsg.setOntology("BUYER_COUNTER");
            counterMsg.setContent(activeSessionId + ";" + buyerTermsForPrice(price).toPayload());
            send(counterMsg);
            log("MANUAL: Sent acceptance price RM" + price);
            
        } else if ("WALKAWAY".equals(action)) {
            sendWalkaway(activeSessionId, "MANUAL_WALKAWAY");
            log("MANUAL: Walked away from negotiation.");
            triggerMarketAction();
            doDelete();
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

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

    private NegotiationTerms buyerTermsForPrice(int price) {
        double progress = (double) Math.max(0, price - initialOffer) / Math.max(1, maxBudget - initialOffer);
        int defaultWarranty = preferences.getDefaultWarrantyMonths();
        int warranty = defaultWarranty + (int) Math.round(defaultWarranty * progress);
        int defaultDelivery = preferences.getDefaultDeliveryDays();
        int delivery = Math.max(3, defaultDelivery - (int) Math.round((defaultDelivery - 3) * progress));
        return new NegotiationTerms(price, warranty, delivery);
    }

    private boolean acceptableUtility(NegotiationTerms terms) {
        double utility = preferences.buyerUtility(terms, maxBudget,
                preferences.getDefaultWarrantyMonths() * 2,
                preferences.getDefaultDeliveryDays());
        return terms.getPrice() <= maxBudget && utility >= 0.35;
    }

    private String termsText(NegotiationTerms terms) {
        return " | Warranty=" + terms.getWarrantyMonths() + " months | Delivery="
                + terms.getDeliveryDays() + " days";
    }
}

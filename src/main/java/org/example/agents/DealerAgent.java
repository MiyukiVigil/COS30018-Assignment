package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import org.example.MainUI.UILogger;

public class DealerAgent extends Agent {
    private String car;
    private int minPrice; // Reserve Price
    private int retailPrice;
    private int currentTargetPrice;
    private UILogger logger;
    private int negotiationCount = 0;
    private NegotiationConfig config = NegotiationConfig.defaults();
    private int stockCount;
    private int manualTargetPrice = -1;
    private NegotiationConfig.Strategy activeStrategy;

    protected void setup() {
        Object[] args = getArguments();
        car = (String) args[0];
        retailPrice = Integer.parseInt((String) args[1]);
        stockCount = Integer.parseInt((String) args[2]); // read stock from args
        logger = (UILogger) args[3];                     // ★ logger is at index 3
        if (args.length > 4 && args[4] instanceof NegotiationConfig) {
            config = (NegotiationConfig) args[4];
        }
        minPrice = (int)(retailPrice * config.getDealerReservePercent());
        currentTargetPrice = retailPrice;
        activeStrategy = config.getStrategy();

        log("STATUS: Registered with retail price RM" + retailPrice + ", reserve: RM" + minPrice
                + " | Strategy: " + config.getStrategy() + strategySwitchText());

        // Register with Broker
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                inform.addReceiver(new AID("broker", AID.ISLOCALNAME));
                inform.setContent(car + ";" + retailPrice + ";" + stockCount + ";" + minPrice);
                send(inform);
                log("STATUS: Listed " + car + " on marketplace | Stock: " + stockCount);

                //Register with SpaceControl after listing
                ACLMessage register = new ACLMessage(ACLMessage.INFORM);
                register.setOntology("REGISTER");
                register.addReceiver(new AID("space", AID.ISLOCALNAME));
                send(register);
            }
        });

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if ("CYCLE_UPDATE".equals(msg.getOntology()) || "START_CYCLE".equals(msg.getOntology())) {
                        int currentCycle = Integer.parseInt(msg.getContent());
                        int t = Math.min(currentCycle, config.getDeadlineCycles());

                        /*
                        The Math:
                        Price(t) = P(initial) - [P(initial) - P(reserve)] * [t / t(max)]^β
                         */
                        NegotiationConfig.Strategy effectiveStrategy = config.getEffectiveStrategy(t);
                        if (effectiveStrategy != activeStrategy) {
                            activeStrategy = effectiveStrategy;
                            log("STATUS: Strategy shifted to " + activeStrategy + " at cycle " + t);
                        }
                        double concessionFactor = Math.pow((double) t / config.getDeadlineCycles(), config.betaForCycle(t));
                        int cycleTarget = (int) (retailPrice - ((retailPrice - minPrice) * concessionFactor));
                        currentTargetPrice = manualTargetPrice >= 0
                                ? Math.max(minPrice, manualTargetPrice)
                                : Math.max(minPrice, (int)(cycleTarget * config.getManualDealerTargetPercent()));
                        log("Dealer Agent " + getLocalName() + " has set vehicle " + car + " to RM" + currentTargetPrice);

                    } else if ("PRICE_ADJUSTMENT".equals(msg.getOntology())) {
                        try {
                            int adjustedPrice = Integer.parseInt(msg.getContent());
                            manualTargetPrice = Math.max(minPrice, adjustedPrice);
                            currentTargetPrice = manualTargetPrice;
                            log("STATUS: Manual target adjusted to RM" + currentTargetPrice);
                        } catch (NumberFormatException e) {
                            log("STATUS: Ignored invalid manual price adjustment: " + msg.getContent());
                        }
                    } else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        negotiationCount++;
                        int buyerOffer = Integer.parseInt(msg.getContent());
                        log("OFFER " + negotiationCount + ": Buyer offered RM" + buyerOffer);

                        if (buyerOffer >= currentTargetPrice) {
                            stockCount--;
                            ACLMessage accept = msg.createReply();
                            accept.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
                            accept.setContent(String.valueOf(buyerOffer));
                            send(accept);
                            // Notify space that this negotiation action completed
                            notifySpaceActionCompleted();
                            log("DEAL CLOSED: Accepted offer of RM" + buyerOffer + " | Stock remaining: " + stockCount);
                            if (stockCount <= 0) {
                                log("STATUS: Out of stock. Closing.");
                                doDelete(); // only delete when stock runs out
                            }
                        } else {
//                        int counter = (retailPrice + buyerOffer) / 2;
                            ACLMessage reject = msg.createReply();
                            reject.setPerformative(ACLMessage.REJECT_PROPOSAL);
                            reject.setContent(String.valueOf(currentTargetPrice));
                            send(reject);
                            // Notify space that this negotiation action completed
                            notifySpaceActionCompleted();
                            log("COUNTER: Offered RM" + currentTargetPrice);
                        }
                    }
                } else block();
            }
        });
    }

    private void notifySpaceActionCompleted() {
        ACLMessage action = new ACLMessage(ACLMessage.INFORM);
        action.setOntology("ACTION_COMPLETED");
        action.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(action);
    }

    //Ignore Inactive Agent so that the cycle continue
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
        if (config.getStrategySwitchCycle() <= 0 || config.getSwitchStrategy() == config.getStrategy()) {
            return "";
        }
        return " -> " + config.getSwitchStrategy() + " at cycle " + config.getStrategySwitchCycle();
    }
}

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

public class BuyerAgent extends Agent {
    private String desiredCar;
    private int maxBudget;
    private UILogger logger;
    private List<DealerOption> dealers = new ArrayList<>();
    private int currentDealerIdx = 0;
    private int negotiationRound = 0;
    private int bestPriceReceived = Integer.MAX_VALUE;
    private int initialOffer;
    private int currentWillingOffer;
    private String bestDealerName = "";
    private boolean dealFound = false;
    private NegotiationConfig config = NegotiationConfig.defaults();
    private int startCycle = -1;
    private int searchRetries = 0;
    private boolean negotiationStarted = true;
    private NegotiationConfig.Strategy activeStrategy;

    private static class DealerOption {
        String name;
        int listedPrice;
        int reservePrice;

        DealerOption(String name, int listedPrice, int reservePrice) {
            this.name = name;
            this.listedPrice = listedPrice;
            this.reservePrice = reservePrice;
        }
    }

    protected void setup() {
        Object[] args = getArguments();
        desiredCar = (String) args[0];
        maxBudget = Integer.parseInt((String) args[1]);
        logger = (UILogger) args[2];
        if (args.length > 3 && args[3] instanceof NegotiationConfig) {
            config = (NegotiationConfig) args[3];
        }
        if (args.length > 4 && args[4] instanceof Boolean) {
            negotiationStarted = !((Boolean) args[4]);
        }

        //Initial Negotiating price
        initialOffer = (int)(maxBudget * config.getBuyerStartPercent());
        currentWillingOffer = initialOffer;
        activeStrategy = config.getStrategy();

        log("STATUS: " + (negotiationStarted ? "Searching" : "Waiting to start") + " for " + desiredCar
                + " (Budget: RM" + maxBudget + ", Strategy: " + config.getStrategy() + strategySwitchText() + ")");

        // 1. Search
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                if (negotiationStarted) {
                    startNegotiationSession();
                }
            }
        });

        // 2. Negotiate with multiple dealers
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if ("START_NEGOTIATION".equals(msg.getOntology())) {
                        if (!negotiationStarted) {
                            startNegotiationSession();
                        } else {
                            log("STATUS: Start ignored because negotiation is already running.");
                        }
                    } else if ("STOP_NEGOTIATION".equals(msg.getOntology())) {
                        log("STATUS: Negotiation stopped by user.");
                        notifyBrokerNoDeal("USER_STOPPED");
                        triggerMarketAction();
                        doDelete();
                    } else if ("CYCLE_UPDATE".equals((msg.getOntology())) && negotiationStarted) {
                        int currentCycle = Integer.parseInt(msg.getContent());

                        if (startCycle == -1) {
                            startCycle = currentCycle;
                        }
                        int localAge = currentCycle - startCycle;
                        int t = Math.min(localAge, config.getDeadlineCycles());
                        /*
                        The Math:
                        Price(t) = P(initial) - [P(initial) - P(reserve)] * [t / t(max)]^β
                         */
                        NegotiationConfig.Strategy effectiveStrategy = config.getEffectiveStrategy(t);
                        if (effectiveStrategy != activeStrategy) {
                            activeStrategy = effectiveStrategy;
                            log("STATUS: Strategy shifted to " + activeStrategy + " at local cycle " + t);
                        }
                        double concessionFactor = Math.pow((double) t / config.getDeadlineCycles(), config.betaForCycle(t));
                        currentWillingOffer = (int) (initialOffer + ((maxBudget - initialOffer) * concessionFactor));
                        if (currentWillingOffer > maxBudget) {
                            currentWillingOffer = maxBudget;
                        }

                        log("Buyer Agent " + getLocalName() + " has set buying price to RM" + currentWillingOffer + " for vehicle " + desiredCar);

                    } else if (msg.getPerformative() == ACLMessage.PROPOSE && negotiationStarted) {
                        // Parse all dealer options
                        String content = msg.getContent();
                        if (!content.equals("NONE")) {
                            dealers.clear();
                            String[] dealerList = content.split(",");
                            for (String dealer : dealerList) {
                                if (!dealer.isEmpty()) {
                                    String[] parts = dealer.split(":");
                                    int listedPrice = parts.length > 1 ? Integer.parseInt(parts[1]) : maxBudget;
                                    int reservePrice = parts.length > 2 ? Integer.parseInt(parts[2]) : listedPrice;
                                    dealers.add(new DealerOption(parts[0], listedPrice, reservePrice));
                                }
                            }
                            if (!dealers.isEmpty()) {
                                boolean affordableDealerExists = false;
                                for (DealerOption dealer : dealers) {
                                    if (dealer.reservePrice <= maxBudget) {
                                        affordableDealerExists = true;
                                        break;
                                    }
                                }
                                if (!affordableDealerExists) {
                                    log("STATUS: Budget too low. Lowest reserve exceeds RM" + maxBudget + ". Ending negotiation.");
                                    notifyBrokerNoDeal("BUDGET_TOO_LOW");
                                    triggerMarketAction();
                                    doDelete();
                                    return;
                                }
                                searchRetries = 0;
                                log("STATUS: Found " + dealers.size() + " dealer(s). Starting negotiations...");
                                currentDealerIdx = 0;
                                startNegotiationWithDealer();
                            }
                        } else {
                            searchRetries++;
                            if (searchRetries > config.getMaxSearchRetries()) {
                                log("STATUS: No matching car found after " + config.getMaxSearchRetries() + " retries. Ending search.");
                                notifyBrokerNoDeal("NO_MATCHING_CAR");
                                triggerMarketAction();
                                doDelete();
                            } else {
                                log("STATUS: No dealers available for " + desiredCar + ". Retry "
                                        + searchRetries + "/" + config.getMaxSearchRetries());
                                addBehaviour(new WakerBehaviour(myAgent, 1500) {
                                    protected void onWake() { searchBroker(); }
                                });
                            }
                        }
                    } else if (msg.getPerformative() == ACLMessage.REJECT_PROPOSAL && negotiationStarted) {
                        handleCounterOffer(msg);
                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL && negotiationStarted) {
                        int finalPrice = Integer.parseInt(msg.getContent());
                        log("SUCCESS! Purchased " + desiredCar + " for RM" + finalPrice + " from " + msg.getSender().getLocalName());
                        notifyBroker(String.valueOf(finalPrice), msg.getSender().getLocalName());
                        dealFound = true;

                        triggerMarketAction();

                        doDelete();
                    }
                } else block();
            }
        });
    }

    private void startNegotiationSession() {
        negotiationStarted = true;
        log("STATUS: Negotiation started for " + desiredCar + ".");
        searchBroker();

        ACLMessage register = new ACLMessage(ACLMessage.INFORM);
        register.setOntology("REGISTER");
        register.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(register);
    }

    public void searchBroker() {
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(new AID("broker", AID.ISLOCALNAME));
        req.setContent(desiredCar);
        send(req);
    }

    private void startNegotiationWithDealer() {
        if (currentDealerIdx < dealers.size()) {
            DealerOption dealer = dealers.get(currentDealerIdx);
            if (dealer.reservePrice > maxBudget) {
                log("STATUS: Skipping " + dealer.name + " because reserve RM" + dealer.reservePrice
                        + " exceeds buyer budget.");
                currentDealerIdx++;
                startNegotiationWithDealer();
                return;
            }
            negotiationRound = 0;
            ACLMessage start = new ACLMessage(ACLMessage.PROPOSE);
            start.addReceiver(new AID(dealer.name, AID.ISLOCALNAME));
//            start.setContent(String.valueOf((int)(maxBudget * 0.7))); // Start at 70% of budget
            start.setContent(String.valueOf(currentWillingOffer));
            send(start);
            // If dealer doesn't respond within a short timeout, move to next dealer
            final DealerOption pending = dealer;
            addBehaviour(new WakerBehaviour(this, 1500) {
                protected void onWake() {
                    if (dealFound) return;
                    if (currentDealerIdx < dealers.size() && dealers.get(currentDealerIdx).name.equals(pending.name)
                            && negotiationRound == 0) {
                        log("STATUS: No response from " + pending.name + ". Moving to next dealer...");
                        currentDealerIdx++;
                        startNegotiationWithDealer();
                    }
                }
            });
            log("NEGOTIATION: Starting with " + dealer.name + " @ RM" + currentWillingOffer);
        } else {
            if (!dealFound) {
                log("STATUS: All negotiations exhausted. No deal reached.");
                notifyBrokerNoDeal("MAX_ROUNDS_REACHED");
                triggerMarketAction();
                doDelete();
            }
        }
    }

    private void handleCounterOffer(ACLMessage msg) {
        int counter = Integer.parseInt(msg.getContent());
        String senderDealer = msg.getSender().getLocalName();
        negotiationRound++;

        log("OFFER: " + senderDealer + " counter-offered RM" + counter + " (Round " + negotiationRound + ")");

        // Track best offer
        if (counter < bestPriceReceived) {
            bestPriceReceived = counter;
            bestDealerName = senderDealer;
        }

        if (counter <= currentWillingOffer) {
            ACLMessage propose = msg.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.valueOf(counter));
            send(propose);
            log("AGREED: Target met! Sending final proposal for RM" + counter);
        } else if (negotiationRound < config.getMaxRoundsPerDealer()) {
            if (negotiationRound >= config.getStuckRoundsBeforeAcceleration()) {
                currentWillingOffer = Math.min(maxBudget,
                        currentWillingOffer + Math.max(1, (maxBudget - currentWillingOffer) / 2));
                log("STATUS: Negotiation dragging. Accelerated buyer offer to RM" + currentWillingOffer);
            }
            ACLMessage propose = msg.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.valueOf(currentWillingOffer));
            send(propose);
            log("COUNTER: Standing firm at RM" + currentWillingOffer);

            triggerMarketAction();
        } else {
            log("STATUS: Max rounds reached with " + senderDealer + ". Moving to next dealer...");
            currentDealerIdx++;
            startNegotiationWithDealer();
        }
    }

    private void notifyBroker(String finalPrice, String dealerName) {
        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.addReceiver(new AID("broker", AID.ISLOCALNAME));
        confirm.setContent(finalPrice + ";" + dealerName + ";" + desiredCar + ";" + negotiationRound);
        send(confirm);
    }

    private void notifyBrokerNoDeal(String reason) {
        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.addReceiver(new AID("broker", AID.ISLOCALNAME));
        confirm.setContent("NO_DEAL;" + reason + ";" + desiredCar + ";" + maxBudget);
        send(confirm);
    }

    private void triggerMarketAction() {
        ACLMessage actionMsg = new ACLMessage(ACLMessage.INFORM);
        actionMsg.setOntology("ACTION_COMPLETED");
        actionMsg.addReceiver(new AID("space", AID.ISLOCALNAME));
        send(actionMsg);
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

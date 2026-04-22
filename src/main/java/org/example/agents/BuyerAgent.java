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
    private List<String> dealers = new ArrayList<>();
    private int currentDealerIdx = 0;
    private int negotiationRound = 0;
    private int bestPriceReceived = Integer.MAX_VALUE;
    private int initialOffer;
    private int currentWillingOffer;
    private String bestDealerName = "";
    private boolean dealFound = false;
    private final int deadlineCycles = 50;
    private final double beta = 2.0;
    private int startCycle = -1;

    protected void setup() {
        Object[] args = getArguments();
        desiredCar = (String) args[0];
        maxBudget = Integer.parseInt((String) args[1]);
        logger = (UILogger) args[2];

        //Initial Negotiating price
        initialOffer = (int)(maxBudget * 0.7);
        currentWillingOffer = initialOffer;

        log("STATUS: Searching for " + desiredCar + " (Budget: RM" + maxBudget + ")");

        // 1. Search
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                searchBroker();

                //Register with SpaceControl after listing
                ACLMessage register = new ACLMessage(ACLMessage.INFORM);
                register.setOntology("REGISTER");
                register.addReceiver(new AID("space", AID.ISLOCALNAME));
                send(register);
            }
        });

        // 2. Negotiate with multiple dealers
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if ("CYCLE_UPDATE".equals((msg.getOntology()))) {
                        int currentCycle = Integer.parseInt(msg.getContent());

                        if (startCycle == -1) {
                            startCycle = currentCycle;
                        }
                        int localAge = currentCycle - startCycle;
                        int t = Math.min(localAge, deadlineCycles);
                        /*
                        The Math:
                        Price(t) = P(initial) - [P(initial) - P(reserve)] * [t / t(max)]^β
                         */
                        double concessionFactor = Math.pow((double) t / deadlineCycles, beta);
                        currentWillingOffer = (int) (initialOffer + ((maxBudget - initialOffer) * concessionFactor));
                        if (currentWillingOffer > maxBudget) {
                            currentWillingOffer = maxBudget;
                        }

                        log("Buyer Agent " + getLocalName() + " has set buying price to RM" + currentWillingOffer + " for vehicle " + desiredCar);

                    } else if (msg.getPerformative() == ACLMessage.PROPOSE) {
                        // Parse all dealer options
                        String content = msg.getContent();
                        if (!content.equals("NONE")) {
                            dealers.clear();
                            String[] dealerList = content.split(",");
                            for (String dealer : dealerList) {
                                if (!dealer.isEmpty()) {
                                    dealers.add(dealer.split(":")[0]);
                                }
                            }
                            if (!dealers.isEmpty()) {
                                log("STATUS: Found " + dealers.size() + " dealer(s). Starting negotiations...");
                                currentDealerIdx = 0;
                                startNegotiationWithDealer();
                            }
                        } else {
                            log("STATUS: No dealers available for " + desiredCar);
//                            doDelete(); //Don't Delete just yet
                            addBehaviour(new WakerBehaviour(myAgent, 5000) {
                                protected void onWake() { searchBroker(); }
                            });
                        }
                    } else if (msg.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                        handleCounterOffer(msg);
                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
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

    public void searchBroker() {
        ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
        req.addReceiver(new AID("broker", AID.ISLOCALNAME));
        req.setContent(desiredCar);
        send(req);
    }

    private void startNegotiationWithDealer() {
        if (currentDealerIdx < dealers.size()) {
            String dealer = dealers.get(currentDealerIdx);
            negotiationRound = 0;
            ACLMessage start = new ACLMessage(ACLMessage.PROPOSE);
            start.addReceiver(new AID(dealer, AID.ISLOCALNAME));
//            start.setContent(String.valueOf((int)(maxBudget * 0.7))); // Start at 70% of budget
            start.setContent(String.valueOf(currentWillingOffer));
            send(start);
            log("NEGOTIATION: Starting with " + dealer + " @ RM" + currentWillingOffer);
        } else {
            if (!dealFound && bestDealerName.isEmpty()) {
                log("STATUS: All negotiations exhausted. No deal reached.");

                currentDealerIdx = 0;

                triggerMarketAction();

                addBehaviour((new WakerBehaviour(this, 3000) {
                    protected void onWake() { searchBroker(); }
                }));
            }
        }
    }

    private void handleCounterOffer(ACLMessage msg) {
        int counter = Integer.parseInt(msg.getContent());
        String senderDealer = msg.getSender().getLocalName();
        negotiationRound++;

        log("OFFER: " + senderDealer + " counter-offered RM" + counter + " (Round " + negotiationRound + ")");

        // Track best offer
//        if (counter < bestPriceReceived) { //How does the system know the bestPriceReceived?
//            bestPriceReceived = counter;
//            bestDealerName = senderDealer;
//        }
//
//        if (counter <= maxBudget) {
//            ACLMessage propose = msg.createReply();
//            propose.setPerformative(ACLMessage.PROPOSE);
//            propose.setContent(String.valueOf(counter));
//            send(propose);
//            log("COUNTER: Agreed to RM" + counter);
//        } else if (negotiationRound < 3) {
//            // Continue negotiating within budget range
//            int nextOffer = (int)(counter * 0.95); // Reduce further
//            if (nextOffer >= (int)(maxBudget * 0.65)) {
//                ACLMessage propose = msg.createReply();
//                propose.setPerformative(ACLMessage.PROPOSE);
//                propose.setContent(String.valueOf(nextOffer));
//                send(propose);
//                log("COUNTER: Offered RM" + nextOffer);
//            } else {
////                log("STATUS: Offer RM" + counter + " exceeds budget. Moving to next dealer...");
////                currentDealerIdx++;
////                startNegotiationWithDealer();
//                moveToNextDealer();
//
//            }
//        } else {
////            log("STATUS: Max rounds reached with " + senderDealer + ". Moving to next dealer...");
////            currentDealerIdx++;
////            startNegotiationWithDealer();
//            moveToNextDealer();
//        }

        if (counter <= currentWillingOffer) {
            ACLMessage propose = msg.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.valueOf(counter));
            send(propose);
            log("AGREED: Target met! Sending final proposal for RM" + counter);
        } else if (negotiationRound < 3) {
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

//    private void moveToNextDealer() {
//        currentDealerIdx++;
//        if (currentDealerIdx < dealers.size()) {
//            startNegotiationWithDealer();
//        } else {
//            log("STATUS: Exhausted dealers. Waiting for cycle market shift before retrying...");
//            currentDealerIdx = 0; // Reset index
//
//            addBehaviour(new WakerBehaviour(this, 3000) {
//                protected void onWake() {
//                    startNegotiationWithDealer();
//                }
//            });
//        }
//    }

    private void notifyBroker(String finalPrice, String dealerName) {
        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.addReceiver(new AID("broker", AID.ISLOCALNAME));
        confirm.setContent(finalPrice + ";" + dealerName + ";" + desiredCar);
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
}
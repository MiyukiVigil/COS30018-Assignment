package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.*;
import org.example.MainUI.UILogger;
import java.util.*;

public class BuyerAgent extends Agent {
    private String desiredCar;
    private int maxBudget;
    private UILogger logger;
    private List<String> dealers = new ArrayList<>();
    private int currentDealerIdx = 0;
    private int negotiationRound = 0;
    private int bestPriceReceived = Integer.MAX_VALUE;
    private String bestDealerName = "";
    private boolean dealFound = false;

    protected void setup() {
        Object[] args = getArguments();
        desiredCar = (String) args[0];
        maxBudget = Integer.parseInt((String) args[1]);
        logger = (UILogger) args[2];

        log("STATUS: Searching for " + desiredCar + " (Budget: RM" + maxBudget + ")");

        // 1. Search
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                ACLMessage req = new ACLMessage(ACLMessage.REQUEST);
                req.addReceiver(new AID("broker", AID.ISLOCALNAME));
                req.setContent(desiredCar);
                send(req);
            }
        });

        // 2. Negotiate with multiple dealers
        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.PROPOSE) {
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
                            doDelete();
                        }
                    } else if (msg.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
                        handleCounterOffer(msg);
                    } else if (msg.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
                        int finalPrice = Integer.parseInt(msg.getContent());
                        log("SUCCESS! Purchased " + desiredCar + " for RM" + finalPrice + " from " + msg.getSender().getLocalName());
                        notifyBroker(String.valueOf(finalPrice));
                        dealFound = true;
                        doDelete();
                    }
                } else block();
            }
        });
    }

    //TODO: Need further clarification on this, maybe there's a better way to implement this.
    private void startNegotiationWithDealer() {
        if (currentDealerIdx < dealers.size()) {
            String dealer = dealers.get(currentDealerIdx);
            negotiationRound = 0;
            ACLMessage start = new ACLMessage(ACLMessage.PROPOSE);
            start.addReceiver(new AID(dealer, AID.ISLOCALNAME));
            start.setContent(String.valueOf((int)(maxBudget * 0.7))); // Start at 70% of budget
            send(start);
            log("NEGOTIATION: Starting with " + dealer + " @ RM" + (int)(maxBudget * 0.7));
        } else {
            if (!dealFound && bestDealerName.isEmpty()) {
                log("STATUS: All negotiations exhausted. No deal reached.");
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

        if (counter <= maxBudget) {
            ACLMessage propose = msg.createReply();
            propose.setPerformative(ACLMessage.PROPOSE);
            propose.setContent(String.valueOf(counter));
            send(propose);
            log("COUNTER: Agreed to RM" + counter);
        } else if (negotiationRound < 3) {
            // Continue negotiating within budget range
            int nextOffer = (int)(counter * 0.95); // Reduce further
            if (nextOffer >= (int)(maxBudget * 0.65)) {
                ACLMessage propose = msg.createReply();
                propose.setPerformative(ACLMessage.PROPOSE);
                propose.setContent(String.valueOf(nextOffer));
                send(propose);
                log("COUNTER: Offered RM" + nextOffer);
            } else {
                log("STATUS: Offer RM" + counter + " exceeds budget. Moving to next dealer...");
                currentDealerIdx++;
                startNegotiationWithDealer();
            }
        } else {
            log("STATUS: Max rounds reached with " + senderDealer + ". Moving to next dealer...");
            currentDealerIdx++;
            startNegotiationWithDealer();
        }
    }

    private void notifyBroker(String finalPrice) {
        ACLMessage confirm = new ACLMessage(ACLMessage.CONFIRM);
        confirm.addReceiver(new AID("broker", AID.ISLOCALNAME));
        confirm.setContent(finalPrice);
        send(confirm);
    }

    private void log(String m) {
        if (logger != null) logger.log(getLocalName() + ": " + m);
    }
}
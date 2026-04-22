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
    private final int deadlineCycles = 50;
    private final double beta = 2.0;
    private int stockCount;

    protected void setup() {
        Object[] args = getArguments();
        car = (String) args[0];
        retailPrice = Integer.parseInt((String) args[1]);
        stockCount = Integer.parseInt((String) args[2]); // read stock from args
        logger = (UILogger) args[3];                     // ★ logger is at index 3
        minPrice = (int)(retailPrice * 0.70);
        currentTargetPrice = retailPrice;

        log("STATUS: Registered with retail price RM" + retailPrice + ", reserve: RM" + minPrice);

        // Register with Broker
        addBehaviour(new OneShotBehaviour() {
            public void action() {
                ACLMessage inform = new ACLMessage(ACLMessage.INFORM);
                inform.addReceiver(new AID("broker", AID.ISLOCALNAME));
                inform.setContent(car + ";" + retailPrice + ";" + stockCount);
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
                        int t = Math.min(currentCycle, deadlineCycles);

                        /*
                        The Math:
                        Price(t) = P(initial) - [P(initial) - P(reserve)] * [t / t(max)]^β
                         */
                        double concessionFactor = Math.pow((double) t / deadlineCycles, beta);
                        currentTargetPrice = (int) (retailPrice - ((retailPrice - minPrice) * concessionFactor));
                        log("Dealer Agent " + getLocalName() + " has set vehicle " + car + " to RM" + currentTargetPrice);

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
                            log("COUNTER: Offered RM" + currentTargetPrice);
                        }
                    }
                } else block();
            }
        });
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
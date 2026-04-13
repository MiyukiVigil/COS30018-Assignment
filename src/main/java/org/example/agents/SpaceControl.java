//This is a space control system that manages the time flow between agents.
//You can stop the space to add agents to the system so that during that cycle, we can add up multiple agents simultaneously to simulate competitive negotiation.
//All negotiation math will be affected by this cycle, for Dealer agent example, as the cycle continue, the value of the car will decrease, and the dealer will be more likely to accept lower offers.
//For buyer agent, as the cycle continue, the buyer will be more likely to accept higher offers.

package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import org.example.MainUI.UILogger;
import java.util.HashSet;
import java.util.Set;

public class SpaceControl extends Agent {
    private final Set<AID> activeAgents = new HashSet<>();
    private final Set<AID> completeAgents = new HashSet<>();
    private int cycleCount = 0;
    private boolean isPaused = false;
    private UILogger logger;

    protected void setup() {
        if (getArguments() != null && getArguments().length > 0) {
            this.logger = (UILogger) getArguments()[0];
        }

        log("Initializing Space Control");

        //Add behavior to JADE
        addBehaviour(
            new CyclicBehaviour() {
                public void action() {
                    ACLMessage msg = receive();
                    if (msg != null) {
                        String ontology = msg.getOntology();

                        if ("REGISTER".equals(ontology)) {
                            activeAgents.add(msg.getSender());
                            log("Registered: " + msg.getSender().getLocalName());

                            if (!isPaused && activeAgents.size() == 1 && cycleCount == 0) {
                                broadcastCycle(1);
                            }
                        } else if ("DEREGISTER".equals(ontology)) {
                            activeAgents.remove(msg.getSender());
                        } else if ("ACTION_COMPLETED".equals(ontology)) {
                            if (!isPaused) {
                                log("Market Action Detected! Auto-advancing cycle.");
                                broadcastCycle(1);
                            } else {
                                log("Market Action Detected, but system is PAUSED. Standing by for manual input.");
                            }
                        }
                    } else {
                        block();
                    }
                }
            }
        );
    }

    private void broadcastCycle(int increment) {
        cycleCount ++;

        ACLMessage updateCycle = new ACLMessage(ACLMessage.PROPAGATE);
        updateCycle.setOntology("CYCLE_UPDATE");
        updateCycle.setContent(String.valueOf(cycleCount));

        for (jade.core.AID agent : activeAgents) {
            updateCycle.addReceiver(agent);
        }

        send(updateCycle);
        log("Cycle Shift: " + cycleCount);
    }

    // Helper method to make logging easier and safer
    private void log(String message) {
        if (logger != null) {
            logger.log(message);
        }
    }
}

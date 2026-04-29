//This is a space control system that manages the time flow between agents.
//You can stop the space to add agents to the system so that during that cycle, we can add up multiple agents simultaneously to simulate competitive negotiation.
//All negotiation math will be affected by this cycle, for Dealer agent example, as the cycle continue, the value of the car will decrease, and the dealer will be more likely to accept lower offers.
//For buyer agent, as the cycle continue, the buyer will be more likely to accept higher offers.

package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.WakerBehaviour;
import org.example.MainUI.UILogger;
import java.util.HashSet;
import java.util.Set;

public class SpaceControl extends Agent {
    private final Set<AID> activeAgents = new HashSet<>();
    private final Set<AID> completeAgents = new HashSet<>();
    private int cycleCount = 0;
    private boolean isPaused = false;
    private boolean cycleAdvancePending = false;
    private final long autoCycleDelayMs = 300;
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
                                // ★ CHANGED: keep cycling after an agent deregisters if others are still active
                                if (!isPaused && !activeAgents.isEmpty()) {
                                    log("Agent left market. Continuing cycle for remaining agents.");
                                    scheduleCycleAdvance();
                                }
                            } else if ("ACTION_COMPLETED".equals(ontology)) {
                                if (!isPaused) {
                                    log("Market Action Detected! Scheduling next cycle.");
                                    scheduleCycleAdvance();
                                } else {
                                    log("Market Action Detected, but system is PAUSED. Standing by for manual input.");
                                }
                            } else if ("PAUSE".equals(ontology)) {
                                isPaused = true;
                                log("Space Control paused.");
                            } else if ("RESUME".equals(ontology)) {
                                isPaused = false;
                                log("Space Control resumed.");
                                if (!activeAgents.isEmpty()) {
                                    scheduleCycleAdvance();
                                }
                            } else if ("STEP".equals(ontology)) {
                                if (!activeAgents.isEmpty()) {
                                    cycleAdvancePending = false;
                                    broadcastCycle(1);
                                }
                            }
                        } else {
                            block();
                        }
                    }
                }
        );
    }

    private void scheduleCycleAdvance() {
        if (cycleAdvancePending || activeAgents.isEmpty()) {
            return;
        }

        cycleAdvancePending = true;
        addBehaviour(new WakerBehaviour(this, autoCycleDelayMs) {
            protected void onWake() {
                cycleAdvancePending = false;
                if (!isPaused && !activeAgents.isEmpty()) {
                    broadcastCycle(1);
                }
            }
        });
    }

    private void broadcastCycle(int increment) {
        cycleCount += increment;

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

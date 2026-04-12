//This is a space control system that manages the time flow between agents.
//You can stop the space to add agents to the system so that during that cycle, we can add up multiple agents simulatineously to simulate competitive negotiation.
//All negotiation math will be affected by this cycle, for Dealer agent example, as the cycle continue, the value of the car will decrease, and the dealer will be more likely to accept lower offers.
//For buyer agent, as the cycle continue, the buyer will be more likely to accept higher offers.

package org.example.agents;

import jade.core.*;
import jade.lang.acl.ACLMessage;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import org.example.MainUI.UILogger;

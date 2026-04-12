package org.example.agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import java.util.ArrayList;
import java.util.List;
import org.example.MainUI.UILogger;

public class BrokerAgent extends Agent {
    private UILogger logger;
    private List<CarListing> inventory = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private double totalRevenue = 0;

    public static class CarListing {
        public String dealer, model;
        public int price;
        public CarListing(String d, String m, int p) {
            this.dealer = d;
            this.model = m;
            this.price = p;
        }
    }

    public static class Transaction {
        public String buyer, dealer, car;
        public int price;
        public long timestamp;
        public Transaction(String b, String d, String c, int p) {
            this.buyer = b;
            this.dealer = d;
            this.car = c;
            this.price = p;
            this.timestamp = System.currentTimeMillis();
        }
    }

    protected void setup() {
        if (getArguments().length > 0) logger = (UILogger) getArguments()[0];
        log("=== BROKER ONLINE ===");
        log("Transaction Fee: RM50/Negotiation | Commission: 5% of sale price");

        addBehaviour(new CyclicBehaviour() {
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM) {
                        // Dealer Registration
                        String[] data = msg.getContent().split(";");
                        inventory.add(new CarListing(msg.getSender().getLocalName(), data[0], Integer.parseInt(data[1])));
                        log("LISTING: " + data[0] + " @ RM" + data[1] + " (Seller: " + msg.getSender().getLocalName() + ")");
                    } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                        // Buyer Search
                        handleSearch(msg);
                    } else if (msg.getPerformative() == ACLMessage.CONFIRM) {
                        // Financial Tracking
                        handleTransaction(msg);
                    }
                } else block();
            }
        });
    }

    //INFO: This function send all of the available dealers to the buyer agent at the time of search?
    private void handleSearch(ACLMessage msg) {
        String target = msg.getContent();
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        for (CarListing cl : inventory) {
            if (cl.model.equalsIgnoreCase(target)) {
                results.append(cl.dealer).append(":").append(cl.price).append(",");
                matchCount++;
            }
        }
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.PROPOSE);
        reply.setContent(results.length() > 0 ? results.toString() : "NONE");
        send(reply);
        
        if (matchCount > 0) {
            log("SEARCH: Found " + matchCount + " " + target + "(s) for buyer " + msg.getSender().getLocalName());
        } else {
            log("SEARCH: No " + target + " available for buyer " + msg.getSender().getLocalName());
        }
    }

    private void handleTransaction(ACLMessage msg) {
        double salePrice = Double.parseDouble(msg.getContent());
        double commission = salePrice * 0.05;
        double totalEarned = commission + 50;
        totalRevenue += totalEarned;
        
        String buyerName = msg.getSender().getLocalName();
        log("DEAL CONFIRMED: Buyer=" + buyerName + " | Sale=RM" + (int)salePrice + " | Commission=RM" + (int)commission + " | Fee=RM50");
        log("REVENUE: RM" + (int)totalEarned + " earned | Total: RM" + (int)totalRevenue);
    }

    private void log(String m) {
        if (logger != null) logger.log("[BROKER] " + m);
    }
}
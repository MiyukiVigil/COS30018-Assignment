package org.example.agents;

import java.util.ArrayList;
import java.util.List;

import org.example.MainUI.UILogger;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

public class BrokerAgent extends Agent {
    private UILogger logger;
    private List<CarListing> inventory = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private double totalRevenue = 0;
    private int noDealCount = 0;
    private int totalDealRounds = 0;

    public static class CarListing {
        public String dealer, model;
        public int price, stock, reservePrice;
        public CarListing(String d, String m, int p, int s, int r) {
            this.dealer = d;
            this.model = m;
            this.price = p;
            this.stock = s;
            this.reservePrice = r;
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

                        // Ignore REGISTER/DEREGISTER messages that leak from other agents
                        String ontology = msg.getOntology();
                        if (ontology == null || ontology.isEmpty()) {
                            String[] data = msg.getContent().split(";");
                            int stock = data.length > 2 ? Integer.parseInt(data[2]) : 1;
                            int price = Integer.parseInt(data[1]);
                            int reserve = data.length > 3 ? Integer.parseInt(data[3]) : (int)(price * 0.70);
                            inventory.add(new CarListing(msg.getSender().getLocalName(), data[0], price, stock, reserve));
                            log("LISTING: " + data[0] + " @ RM" + data[1] + " | Reserve: RM" + reserve
                                    + " | Stock: " + stock + " (Seller: " + msg.getSender().getLocalName() + ")");
                        }

                    } else if (msg.getPerformative() == ACLMessage.REQUEST) {
                        // Buyer Search
                        handleSearch(msg);
                    } else if (msg.getPerformative() == ACLMessage.CONFIRM) {
                        // Financial Tracking
                        log("CONFIRM received from: " + msg.getSender().getLocalName() + " | Content: " + msg.getContent());
                        handleTransaction(msg);
                    }
                } else block();
            }
        });
    }

    //INFO: This function send all of the available seller dealer's offers to the buyer agent at the time of search?
    private void handleSearch(ACLMessage msg) {
        String target = msg.getContent();
        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        for (CarListing cl : inventory) {
            if (cl.model.equalsIgnoreCase(target) && cl.stock > 0) {
                results.append(cl.dealer).append(":").append(cl.price).append(":").append(cl.reservePrice).append(",");
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
        try {
            String[] parts = msg.getContent().split(";");
            if ("NO_DEAL".equals(parts[0])) {
                noDealCount++;
                String reason = parts.length > 1 ? parts[1] : "UNKNOWN";
                String carModel = parts.length > 2 ? parts[2] : "Unknown";
                log("NO DEAL RECORDED: Buyer=" + msg.getSender().getLocalName()
                        + " | Car=" + carModel + " | Reason=" + reason);
                logPerformanceMetrics();
                return;
            }

            double salePrice = Double.parseDouble(parts[0]);
            String dealerName = parts.length > 1 ? parts[1] : "Unknown";
            String carModel   = parts.length > 2 ? parts[2] : "Unknown";
            int rounds = parts.length > 3 ? Integer.parseInt(parts[3]) : 0;

            double commission = salePrice * 0.05;
            double totalEarned = commission + 50;
            totalRevenue += totalEarned;
            totalDealRounds += rounds;
            reduceStock(dealerName, carModel);

            String buyerName = msg.getSender().getLocalName();
            transactions.add(new Transaction(buyerName, dealerName, carModel, (int)salePrice));

            log("DEAL CONFIRMED: Buyer=" + buyerName + " | Dealer=" + dealerName + " | Car=" + carModel + " | Sale=RM" + (int)salePrice + " | Commission=RM" + (int)commission + " | Fee=RM50");
            log("REVENUE: RM" + (int)totalEarned + " earned | Total: RM" + (int)totalRevenue); // ★ CHANGED: kept for revenue tracking
            log("TOTAL TRANSACTIONS RECORDED: " + transactions.size());
            logPerformanceMetrics();
        } catch (Exception e) {
            log("ERROR in handleTransaction: " + e.getMessage() + " | Raw content: " + msg.getContent()); // ★ CHANGED: catch errors silently before
        }
    }

    private void reduceStock(String dealerName, String carModel) {
        for (CarListing listing : inventory) {
            if (listing.dealer.equals(dealerName) && listing.model.equalsIgnoreCase(carModel) && listing.stock > 0) {
                listing.stock--;
                return;
            }
        }
    }

    private void logPerformanceMetrics() {
        int totalAttempts = transactions.size() + noDealCount;
        double averageDealPrice = transactions.stream().mapToInt(t -> t.price).average().orElse(0);
        double averageRounds = transactions.isEmpty() ? 0 : (double) totalDealRounds / transactions.size();
        double successRate = totalAttempts == 0 ? 0 : (transactions.size() * 100.0) / totalAttempts;
        log(String.format("PERFORMANCE: Deals=%d | NoDeals=%d | AvgDeal=RM%.0f | AvgRounds=%.1f | SuccessRate=%.1f%%",
                transactions.size(), noDealCount, averageDealPrice, averageRounds, successRate));
    }

    private void log(String m) {
        if (logger != null) logger.log("[BROKER] " + m);
    }
}

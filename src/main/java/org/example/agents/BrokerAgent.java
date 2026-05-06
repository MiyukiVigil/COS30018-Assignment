package org.example.agents;

import java.util.*;
import org.example.MainUI.UILogger;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * BrokerAgent — Phase 1
 *
 * Protocol contract: docs/protocol-contract.md
 *
 * Message ontologies handled (inbound):
 *   ""               — Dealer registration (INFORM, empty ontology)
 *   BUYER_SEARCH     — Buyer requests shortlist (REQUEST)
 *   BUYER_SHORTLIST  — Buyer selects dealer + first offer (PROPOSE) → creates session
 *   DEALER_COUNTER   — Dealer rejects with counter (REJECT_PROPOSAL) → relayed to buyer
 *   DEALER_ACCEPT    — Dealer accepts offer (ACCEPT_PROPOSAL) → settle session
 *   BUYER_COUNTER    — Buyer counter-offer (PROPOSE) → relayed to dealer
 *   BUYER_WALKAWAY   — Buyer gives up on session (FAILURE) → close session
 */
public class BrokerAgent extends Agent {

    // ── Fee constants ──────────────────────────────────────────────────────────
    public static final double FIXED_FEE    = 50.0;
    public static final double COMMISSION_RATE = 0.05;

    // ── State ──────────────────────────────────────────────────────────────────
    private UILogger logger;
    private final List<CarListing>              inventory    = new ArrayList<>();
    private final Map<String, NegotiationSession> sessions   = new LinkedHashMap<>();
    private final List<Transaction>             transactions = new ArrayList<>();

    private double totalRevenue    = 0;
    private int    noDealCount     = 0;
    private int    totalDealRounds = 0;

    // ── Inner types ────────────────────────────────────────────────────────────

    public static class CarListing {
        public String dealer, model;
        public int price, stock, reservePrice;
        public CarListing(String d, String m, int p, int s, int r) {
            dealer = d; model = m; price = p; stock = s; reservePrice = r;
        }
    }

    public enum SessionStatus { NEGOTIATING, SETTLED, FAILED, TIMEOUT }

    public static class NegotiationSession {
        public final String sessionId;
        public final String buyerId;
        public final String dealerId;
        public final String carModel;
        public int    currentOffer;
        public int    buyerReserve;
        public int    round;
        public SessionStatus status;
        public final long startTime;
        public boolean feeCharged;

        public NegotiationSession(String sid, String buyer, String dealer,
                                  String car, int firstOffer, int reserve) {
            sessionId   = sid;
            buyerId     = buyer;
            dealerId    = dealer;
            carModel    = car;
            currentOffer = firstOffer;
            buyerReserve = reserve;
            round       = 0;
            status      = SessionStatus.NEGOTIATING;
            startTime   = System.currentTimeMillis();
            feeCharged  = false;
        }
    }

    public static class Transaction {
        public String buyer, dealer, car;
        public int price;
        public long timestamp;
        public Transaction(String b, String d, String c, int p) {
            buyer = b; dealer = d; car = c; price = p;
            timestamp = System.currentTimeMillis();
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void setup() {
        if (getArguments() != null && getArguments().length > 0) {
            logger = (UILogger) getArguments()[0];
        }
        log("=== BROKER ONLINE ===");
        log(String.format("Fixed Negotiation Fee: RM%.0f | Commission: %.0f%% of sale price",
                FIXED_FEE, COMMISSION_RATE * 100));

        addBehaviour(new CyclicBehaviour() {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg == null) { block(); return; }

                String ont = msg.getOntology() == null ? "" : msg.getOntology();
                switch (ont) {
                    case "":               handleDealerRegister(msg);  break;
                    case "BUYER_SEARCH":   handleBuyerSearch(msg);     break;
                    case "BUYER_SHORTLIST":handleBuyerShortlist(msg);  break;
                    case "DEALER_COUNTER": handleDealerCounter(msg);   break;
                    case "DEALER_ACCEPT":  handleDealerAccept(msg);    break;
                    case "DEALER_SOLD_OUT":handleDealerSoldOut(msg);   break;
                    case "BUYER_COUNTER":  handleBuyerCounter(msg);    break;
                    case "BUYER_WALKAWAY": handleBuyerWalkaway(msg);   break;
                    default: break; // ignore REGISTER/DEREGISTER/CYCLE_UPDATE leaks
                }
            }
        });
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    /** Dealer registration: INFORM / empty ontology / "car;price;stock;reserve" */
    private void handleDealerRegister(ACLMessage msg) {
        if (msg.getPerformative() != ACLMessage.INFORM) return;
        String[] d = msg.getContent().split(";");
        if (d.length < 2) return;
        int price   = Integer.parseInt(d[1]);
        int stock   = d.length > 2 ? Integer.parseInt(d[2]) : 1;
        int reserve = d.length > 3 ? Integer.parseInt(d[3]) : (int)(price * 0.70);
        inventory.add(new CarListing(msg.getSender().getLocalName(), d[0], price, stock, reserve));
        log("LISTING: " + d[0] + " @ RM" + price + " | Reserve: RM" + reserve
                + " | Stock: " + stock + " (Seller: " + msg.getSender().getLocalName() + ")");
    }

    /** Buyer search: REQUEST / BUYER_SEARCH / "sessionId;carModel" */
    private void handleBuyerSearch(ACLMessage msg) {
        String[] parts = msg.getContent().split(";", 2);
        String sessionId = parts[0];
        String carModel  = parts.length > 1 ? parts[1] : parts[0];

        StringBuilder results = new StringBuilder();
        int matchCount = 0;
        for (CarListing cl : inventory) {
            if (cl.model.equalsIgnoreCase(carModel) && cl.stock > 0) {
                results.append(cl.dealer).append(":").append(cl.price)
                       .append(":").append(cl.reservePrice).append(",");
                matchCount++;
            }
        }
        ACLMessage reply = msg.createReply();
        reply.setPerformative(ACLMessage.PROPOSE);
        reply.setOntology("BROKER_SHORTLIST");
        reply.setContent(sessionId + ";" + (results.length() > 0 ? results.toString() : "NONE"));
        send(reply);

        log(matchCount > 0
                ? "SEARCH: Found " + matchCount + " " + carModel + "(s) for " + msg.getSender().getLocalName()
                : "SEARCH: No " + carModel + " available for " + msg.getSender().getLocalName());
    }

    /**
     * Buyer shortlist submission: PROPOSE / BUYER_SHORTLIST
     * Content: "sessionId;dealerName;firstOffer;buyerReserve;carModel"
     * → Creates session, charges fixed fee, invites dealer.
     */
    private void handleBuyerShortlist(ACLMessage msg) {
        String[] p = msg.getContent().split(";");
        if (p.length < 5) {
            log("ERROR: Malformed BUYER_SHORTLIST: " + msg.getContent());
            return;
        }
        String sessionId  = p[0];
        String dealerName = p[1];
        int firstOffer    = Integer.parseInt(p[2]);
        int buyerReserve  = Integer.parseInt(p[3]);
        String carModel   = p[4];
        String buyerName  = msg.getSender().getLocalName();

        NegotiationSession session = new NegotiationSession(
                sessionId, buyerName, dealerName, carModel, firstOffer, buyerReserve);
        sessions.put(sessionId, session);

        // Charge fixed fee at session start
        totalRevenue += FIXED_FEE;
        session.feeCharged = true;
        log("SESSION START: " + sessionId + " | Buyer=" + buyerName
                + " | Dealer=" + dealerName + " | Car=" + carModel + " | FirstOffer=RM" + firstOffer);
        log("FEE CHARGED: RM" + (int) FIXED_FEE + " | Running Revenue: RM" + (int) totalRevenue);

        // Invite dealer with buyer's first offer
        ACLMessage invite = new ACLMessage(ACLMessage.REQUEST);
        invite.addReceiver(new AID(dealerName, AID.ISLOCALNAME));
        invite.setOntology("BROKER_INVITE");
        invite.setContent(sessionId + ";" + buyerName + ";" + carModel + ";" + firstOffer);
        send(invite);
        log("INVITE: Sent RM" + firstOffer + " offer to " + dealerName);
    }

    /**
     * Dealer counter: REJECT_PROPOSAL / DEALER_COUNTER / "sessionId;counterPrice"
     * → Relay counter to buyer.
     */
    private void handleDealerCounter(ACLMessage msg) {
        String[] p = msg.getContent().split(";", 2);
        String sessionId = p[0];
        int counter      = Integer.parseInt(p[1]);
        NegotiationSession s = sessions.get(sessionId);
        if (s == null) { log("ERROR: Unknown session in DEALER_COUNTER: " + sessionId); return; }

        s.round++;
        s.currentOffer = counter;
        log("RELAY COUNTER: " + sessionId + " | Dealer=" + s.dealerId
                + " → Buyer=" + s.buyerId + " | RM" + counter + " (Round " + s.round + ")");

        ACLMessage relay = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
        relay.addReceiver(new AID(s.buyerId, AID.ISLOCALNAME));
        relay.setOntology("BROKER_RELAY_COUNTER");
        relay.setContent(sessionId + ";" + s.dealerId + ";" + counter);
        send(relay);
    }

    /**
     * Dealer accept: ACCEPT_PROPOSAL / DEALER_ACCEPT / "sessionId;agreedPrice"
     * → Charge commission, settle session, notify buyer.
     */
    private void handleDealerAccept(ACLMessage msg) {
        String[] p    = msg.getContent().split(";", 2);
        String sessionId  = p[0];
        int agreedPrice   = Integer.parseInt(p[1]);
        NegotiationSession s = sessions.get(sessionId);
        if (s == null) { log("ERROR: Unknown session in DEALER_ACCEPT: " + sessionId); return; }

        s.round++;
        s.status = SessionStatus.SETTLED;
        double commission = agreedPrice * COMMISSION_RATE;
        totalRevenue   += commission;
        totalDealRounds += s.round;
        reduceStock(s.dealerId, s.carModel);
        transactions.add(new Transaction(s.buyerId, s.dealerId, s.carModel, agreedPrice));

        log("DEAL SETTLED: " + sessionId + " | Buyer=" + s.buyerId + " | Dealer=" + s.dealerId
                + " | Car=" + s.carModel + " | Price=RM" + agreedPrice
                + " | Commission=RM" + (int) commission + " | Fee=RM" + (int) FIXED_FEE + " (charged at start)");
        log("REVENUE: +RM" + (int) commission + " commission | Total: RM" + (int) totalRevenue);
        log("TOTAL TRANSACTIONS: " + transactions.size());
        logPerformanceMetrics();

        // Notify buyer
        ACLMessage notify = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
        notify.addReceiver(new AID(s.buyerId, AID.ISLOCALNAME));
        notify.setOntology("BROKER_RELAY_ACCEPT");
        notify.setContent(sessionId + ";" + s.dealerId + ";" + agreedPrice);
        send(notify);
    }

    /**
     * Buyer counter: PROPOSE / BUYER_COUNTER / "sessionId;newOffer"
     * → Relay new offer to dealer.
     */
    private void handleBuyerCounter(ACLMessage msg) {
        String[] p = msg.getContent().split(";", 2);
        String sessionId = p[0];
        int newOffer     = Integer.parseInt(p[1]);
        NegotiationSession s = sessions.get(sessionId);
        if (s == null) { log("ERROR: Unknown session in BUYER_COUNTER: " + sessionId); return; }

        s.currentOffer = newOffer;
        log("RELAY OFFER: " + sessionId + " | Buyer=" + s.buyerId
                + " → Dealer=" + s.dealerId + " | RM" + newOffer);

        ACLMessage relay = new ACLMessage(ACLMessage.PROPOSE);
        relay.addReceiver(new AID(s.dealerId, AID.ISLOCALNAME));
        relay.setOntology("BROKER_RELAY_OFFER");
        relay.setContent(sessionId + ";" + s.buyerId + ";" + s.carModel + ";" + newOffer);
        send(relay);
    }

    /**
     * Buyer walkaway: FAILURE / BUYER_WALKAWAY / "sessionId;reason"
     * → Mark session FAILED. Fixed fee already collected.
     */
    private void handleBuyerWalkaway(ACLMessage msg) {
        String[] p = msg.getContent().split(";", 2);
        String sessionId = p[0];
        String reason    = p.length > 1 ? p[1] : "UNKNOWN";
        NegotiationSession s = sessions.get(sessionId);
        if (s != null) {
            s.status = SessionStatus.FAILED;
            noDealCount++;
            log("NO DEAL: " + sessionId + " | Reason=" + reason
                    + " | Buyer=" + s.buyerId + " | Car=" + s.carModel);
            logPerformanceMetrics();
        }
    }

    /**
     * Dealer sold out: INFORM / DEALER_SOLD_OUT / "sessionId1,sessionId2,..."
     * Dealer went out of stock mid-negotiation. Close every listed session and
     * notify the affected buyers so they can immediately try the next dealer.
     */
    private void handleDealerSoldOut(ACLMessage msg) {
        String dealerName = msg.getSender().getLocalName();
        String[] sessionIds = msg.getContent().split(",");
        for (String sessionId : sessionIds) {
            sessionId = sessionId.trim();
            if (sessionId.isEmpty()) continue;
            NegotiationSession s = sessions.get(sessionId);
            if (s == null) continue;
            if (s.status != SessionStatus.NEGOTIATING) continue; // already closed

            s.status = SessionStatus.FAILED;
            noDealCount++;
            log("NO DEAL: " + sessionId + " | Reason=DEALER_SOLD_OUT | Buyer=" + s.buyerId
                    + " | Dealer=" + dealerName + " | Car=" + s.carModel);
            logPerformanceMetrics();

            // Notify buyer so they advance to the next dealer without waiting
            ACLMessage notify = new ACLMessage(ACLMessage.INFORM);
            notify.addReceiver(new AID(s.buyerId, AID.ISLOCALNAME));
            notify.setOntology("BROKER_RELAY_SOLD_OUT");
            notify.setContent(sessionId + ";" + dealerName);
            send(notify);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void reduceStock(String dealerName, String carModel) {
        for (CarListing cl : inventory) {
            if (cl.dealer.equals(dealerName)
                    && cl.model.equalsIgnoreCase(carModel)
                    && cl.stock > 0) {
                cl.stock--;
                return;
            }
        }
    }

    private void logPerformanceMetrics() {
        int total = transactions.size() + noDealCount;
        double avgPrice  = transactions.stream().mapToInt(t -> t.price).average().orElse(0);
        double avgRounds = transactions.isEmpty() ? 0 : (double) totalDealRounds / transactions.size();
        double successRate = total == 0 ? 0 : (transactions.size() * 100.0) / total;
        log(String.format(
                "PERFORMANCE: Deals=%d | NoDeals=%d | AvgDeal=RM%.0f | AvgRounds=%.1f | SuccessRate=%.1f%%",
                transactions.size(), noDealCount, avgPrice, avgRounds, successRate));
    }

    private void log(String m) {
        if (logger != null) logger.log("[BROKER] " + m);
    }

    // ── Public accessors (for GUI dashboard, Phase 4) ─────────────────────────

    public List<CarListing>                    getInventory()    { return Collections.unmodifiableList(inventory); }
    public Map<String, NegotiationSession>     getSessions()     { return Collections.unmodifiableMap(sessions); }
    public List<Transaction>                   getTransactions() { return Collections.unmodifiableList(transactions); }
    public double getTotalRevenue()   { return totalRevenue; }
    public int    getNoDealCount()    { return noDealCount; }
}

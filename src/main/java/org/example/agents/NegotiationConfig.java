package org.example.agents;

import java.io.Serializable;

public class NegotiationConfig implements Serializable {
    public enum Strategy {
        BOULWARE,
        CONCEDER,
        LINEAR
    }

    private final Strategy strategy;
    private final int deadlineCycles;
    private final double buyerStartPercent;
    private final double dealerReservePercent;
    private final int maxRoundsPerDealer;
    private final int maxSearchRetries;
    private final int stuckRoundsBeforeAcceleration;
    private final double manualDealerTargetPercent;
    private final int strategySwitchCycle;
    private final Strategy switchStrategy;

    public NegotiationConfig(
            Strategy strategy,
            int deadlineCycles,
            double buyerStartPercent,
            double dealerReservePercent,
            int maxRoundsPerDealer,
            int maxSearchRetries,
            int stuckRoundsBeforeAcceleration,
            double manualDealerTargetPercent
    ) {
        this(strategy, deadlineCycles, buyerStartPercent, dealerReservePercent, maxRoundsPerDealer,
                maxSearchRetries, stuckRoundsBeforeAcceleration, manualDealerTargetPercent, 0, strategy);
    }

    public NegotiationConfig(
            Strategy strategy,
            int deadlineCycles,
            double buyerStartPercent,
            double dealerReservePercent,
            int maxRoundsPerDealer,
            int maxSearchRetries,
            int stuckRoundsBeforeAcceleration,
            double manualDealerTargetPercent,
            int strategySwitchCycle,
            Strategy switchStrategy
    ) {
        this.strategy = strategy;
        this.deadlineCycles = deadlineCycles;
        this.buyerStartPercent = buyerStartPercent;
        this.dealerReservePercent = dealerReservePercent;
        this.maxRoundsPerDealer = maxRoundsPerDealer;
        this.maxSearchRetries = maxSearchRetries;
        this.stuckRoundsBeforeAcceleration = stuckRoundsBeforeAcceleration;
        this.manualDealerTargetPercent = manualDealerTargetPercent;
        this.strategySwitchCycle = strategySwitchCycle;
        this.switchStrategy = switchStrategy;
    }

    public static NegotiationConfig defaults() {
        return new NegotiationConfig(Strategy.BOULWARE, 50, 0.70, 0.70, 3, 2, 2, 1.0,
                15, Strategy.CONCEDER);
    }

    public double beta() {
        return betaForStrategy(strategy);
    }

    public double betaForCycle(int cycle) {
        return betaForStrategy(getEffectiveStrategy(cycle));
    }

    public Strategy getEffectiveStrategy(int cycle) {
        if (strategySwitchCycle > 0 && cycle >= strategySwitchCycle) {
            return switchStrategy;
        }
        return strategy;
    }

    private double betaForStrategy(Strategy activeStrategy) {
        switch (activeStrategy) {
            case CONCEDER:
                return 0.45;
            case LINEAR:
                return 1.0;
            case BOULWARE:
            default:
                return 2.0;
        }
    }

    public Strategy getStrategy() {
        return strategy;
    }

    public int getDeadlineCycles() {
        return deadlineCycles;
    }

    public double getBuyerStartPercent() {
        return buyerStartPercent;
    }

    public double getDealerReservePercent() {
        return dealerReservePercent;
    }

    public int getMaxRoundsPerDealer() {
        return maxRoundsPerDealer;
    }

    public int getMaxSearchRetries() {
        return maxSearchRetries;
    }

    public int getStuckRoundsBeforeAcceleration() {
        return stuckRoundsBeforeAcceleration;
    }

    public double getManualDealerTargetPercent() {
        return manualDealerTargetPercent;
    }

    public int getStrategySwitchCycle() {
        return strategySwitchCycle;
    }

    public Strategy getSwitchStrategy() {
        return switchStrategy;
    }
}

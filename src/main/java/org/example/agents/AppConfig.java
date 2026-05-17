package org.example.agents;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class AppConfig {
    private static final String RESOURCE_NAME = "negotiation-defaults.properties";
    private static final AppConfig INSTANCE = load();

    private final Properties values;

    private AppConfig(Properties values) {
        this.values = values;
    }

    public static AppConfig defaults() {
        return INSTANCE;
    }

    private static AppConfig load() {
        Properties props = new Properties();
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(RESOURCE_NAME)) {
            if (in != null) {
                props.load(in);
            }
        } catch (IOException ignored) {
            // Hardcoded fallbacks below keep the demo launchable if the resource is missing.
        }
        return new AppConfig(props);
    }

    public double fixedFee() {
        return getDouble("broker.fixedFee", 50.0);
    }

    public double commissionRate() {
        return getDouble("broker.commissionRate", 0.05);
    }

    public long sessionTimeoutMillis() {
        return getLong("broker.sessionTimeoutMillis", 120000L);
    }

    public long timeoutScanMillis() {
        return getLong("broker.timeoutScanMillis", 5000L);
    }

    public NegotiationConfig negotiationConfig() {
        NegotiationConfig.Strategy strategy = getStrategy("strategy.initial", NegotiationConfig.Strategy.BOULWARE);
        NegotiationConfig.Strategy switchStrategy = getStrategy("strategy.switch", NegotiationConfig.Strategy.CONCEDER);
        return new NegotiationConfig(
                strategy,
                getInt("strategy.deadlineCycles", 50),
                getDouble("strategy.buyerStartPercent", 0.70),
                getDouble("strategy.dealerReservePercent", 0.70),
                getInt("strategy.maxRoundsPerDealer", 3),
                getInt("strategy.maxSearchRetries", 2),
                getInt("strategy.stuckRoundsBeforeAcceleration", 2),
                getDouble("strategy.manualDealerTargetPercent", 1.0),
                getInt("strategy.switchCycle", 15),
                switchStrategy);
    }

    public UtilityPreferences utilityPreferences() {
        return new UtilityPreferences(
                getDouble("utility.priceWeight", 0.70),
                getDouble("utility.warrantyWeight", 0.20),
                getDouble("utility.deliveryWeight", 0.10),
                getInt("utility.defaultWarrantyMonths", 12),
                getInt("utility.defaultDeliveryDays", 14));
    }

    private NegotiationConfig.Strategy getStrategy(String key, NegotiationConfig.Strategy fallback) {
        try {
            return NegotiationConfig.Strategy.valueOf(values.getProperty(key, fallback.name()).trim());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(values.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private long getLong(String key, long fallback) {
        try {
            return Long.parseLong(values.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double getDouble(String key, double fallback) {
        try {
            return Double.parseDouble(values.getProperty(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}

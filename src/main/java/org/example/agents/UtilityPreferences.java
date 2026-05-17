package org.example.agents;

import java.io.Serializable;

public class UtilityPreferences implements Serializable {
    private final double priceWeight;
    private final double warrantyWeight;
    private final double deliveryWeight;
    private final int defaultWarrantyMonths;
    private final int defaultDeliveryDays;

    public UtilityPreferences(double priceWeight, double warrantyWeight, double deliveryWeight,
                              int defaultWarrantyMonths, int defaultDeliveryDays) {
        double total = Math.max(0.0001, priceWeight + warrantyWeight + deliveryWeight);
        this.priceWeight = priceWeight / total;
        this.warrantyWeight = warrantyWeight / total;
        this.deliveryWeight = deliveryWeight / total;
        this.defaultWarrantyMonths = Math.max(0, defaultWarrantyMonths);
        this.defaultDeliveryDays = Math.max(0, defaultDeliveryDays);
    }

    public double buyerUtility(NegotiationTerms terms, int maxBudget, int maxWarrantyMonths, int maxDeliveryDays) {
        double priceScore = clamp((double) (maxBudget - terms.getPrice()) / Math.max(1, maxBudget));
        double warrantyScore = clamp((double) terms.getWarrantyMonths() / Math.max(1, maxWarrantyMonths));
        double deliveryScore = clamp((double) (maxDeliveryDays - terms.getDeliveryDays()) / Math.max(1, maxDeliveryDays));
        return priceScore * priceWeight + warrantyScore * warrantyWeight + deliveryScore * deliveryWeight;
    }

    public double dealerUtility(NegotiationTerms terms, int retailPrice, int reservePrice,
                                int maxWarrantyMonths, int maxDeliveryDays) {
        double priceScore = clamp((double) (terms.getPrice() - reservePrice) / Math.max(1, retailPrice - reservePrice));
        double warrantyScore = clamp((double) (maxWarrantyMonths - terms.getWarrantyMonths()) / Math.max(1, maxWarrantyMonths));
        double deliveryScore = clamp((double) terms.getDeliveryDays() / Math.max(1, maxDeliveryDays));
        return priceScore * priceWeight + warrantyScore * warrantyWeight + deliveryScore * deliveryWeight;
    }

    public int getDefaultWarrantyMonths() {
        return defaultWarrantyMonths;
    }

    public int getDefaultDeliveryDays() {
        return defaultDeliveryDays;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

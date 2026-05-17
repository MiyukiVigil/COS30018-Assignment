package org.example.agents;

import java.io.Serializable;

public class NegotiationTerms implements Serializable {
    private final int price;
    private final int warrantyMonths;
    private final int deliveryDays;

    public NegotiationTerms(int price, int warrantyMonths, int deliveryDays) {
        this.price = price;
        this.warrantyMonths = Math.max(0, warrantyMonths);
        this.deliveryDays = Math.max(0, deliveryDays);
    }

    public static NegotiationTerms priceOnly(int price) {
        UtilityPreferences defaults = AppConfig.defaults().utilityPreferences();
        return new NegotiationTerms(price, defaults.getDefaultWarrantyMonths(), defaults.getDefaultDeliveryDays());
    }

    public int getPrice() {
        return price;
    }

    public int getWarrantyMonths() {
        return warrantyMonths;
    }

    public int getDeliveryDays() {
        return deliveryDays;
    }

    public String toPayload() {
        return price + ":" + warrantyMonths + ":" + deliveryDays;
    }

    public static NegotiationTerms fromPayload(String payload) {
        String[] parts = payload.split(":");
        int price = Integer.parseInt(parts[0]);
        UtilityPreferences defaults = AppConfig.defaults().utilityPreferences();
        int warranty = parts.length > 1 ? Integer.parseInt(parts[1]) : defaults.getDefaultWarrantyMonths();
        int delivery = parts.length > 2 ? Integer.parseInt(parts[2]) : defaults.getDefaultDeliveryDays();
        return new NegotiationTerms(price, warranty, delivery);
    }
}

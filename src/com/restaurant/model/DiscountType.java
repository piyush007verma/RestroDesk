package com.restaurant.model;

/** The kind of discount the cashier chose. Stored in bills.discount_type. */
public enum DiscountType {
    NONE("No discount"),
    PERCENT("Percentage (%)"),
    FIXED("Fixed amount (₹)");

    private final String label;

    DiscountType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}

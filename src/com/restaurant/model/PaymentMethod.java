package com.restaurant.model;

/** How the customer paid. Only RECORDED in MySQL - there is no real bank, UPI or card processing in this project. */
public enum PaymentMethod {
    CASH("Cash"),
    UPI("UPI"),
    CARD("Card");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public String toString() {
        return label; // what a drop-down shows
    }
}

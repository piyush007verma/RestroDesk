package com.restaurant.ui;

/** The list of screens, in the same order as the sidebar. Adding a screen = adding one line here. */
public enum Screen {
    DASHBOARD("Dashboard", "D"),
    MENU("Menu", "M"),
    TABLES("Tables", "T"),
    ORDERS("Orders", "O"),
    BILLING("Billing", "B"),
    HISTORY("Order History", "H"),
    REPORTS("Reports", "R");

    private final String label;
    private final String badge;

    Screen(String label, String badge) {
        this.label = label;
        this.badge = badge;
    }

    public String getLabel() {
        return label;
    }

    /** One letter shown in the sidebar icon badge. */
    public String getBadge() {
        return badge;
    }
}

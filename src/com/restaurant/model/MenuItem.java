package com.restaurant.model;

import java.math.BigDecimal;

/**
 * One dish on the restaurant menu (maps to the menu_items DB table).
 * Fields are private and only readable through getters (encapsulation).
 * A brand-new item that is not saved yet has id 0.
 */
public class MenuItem {

    private final int id;
    private final String name;
    private final String category;
    private final BigDecimal price;
    private final boolean available;

    public MenuItem(int id, String name, String category, BigDecimal price, boolean available) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.available = available;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /** true = can be ordered now; false = temporarily not served (still kept on the menu). */
    public boolean isAvailable() {
        return available;
    }
}

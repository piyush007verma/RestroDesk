package com.restaurant.model;

import java.math.BigDecimal;

/**
 * One food item inside an order (maps to a row of order_items joined with its menu item).
 * The price is the unit_price COPIED when the item was first added, so changing the menu price later
 * never changes an order that already exists.
 */
public class OrderLine {

    /** No more than this many of one dish on one order (a typing mistake such as 500 is almost surely wrong). */
    public static final int MAX_QUANTITY = 99;

    private final int itemId;
    private final String name;
    private final String category;
    private final BigDecimal unitPrice;
    private final int quantity;

    public OrderLine(int itemId, String name, String category, BigDecimal unitPrice, int quantity) {
        this.itemId = itemId;
        this.name = name;
        this.category = category;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public int getItemId() {
        return itemId;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    /** price x quantity, calculated here so every screen shows the same number. */
    public BigDecimal getSubtotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}

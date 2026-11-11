package com.restaurant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the "Active Orders" list: who, where, when, how many items and how much.
 * The numbers come from the order_items rows of the order (quantity and unit_price), because the
 * orders table stores no total (see HANDOFF.md).
 */
public class OrderSummary {

    private final int orderId;
    private final int tableNumber;
    private final String customerName;
    private final String customerPhone;   // null when not given
    private final OrderStatus status;
    private final LocalDateTime createdAt;
    private final int itemCount;          // total quantity, e.g. 2 burgers + 1 tea = 3
    private final BigDecimal total;

    public OrderSummary(int orderId, int tableNumber, String customerName, String customerPhone,
                        OrderStatus status, LocalDateTime createdAt, int itemCount, BigDecimal total) {
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.status = status;
        this.createdAt = createdAt;
        this.itemCount = itemCount;
        this.total = total;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public int getItemCount() {
        return itemCount;
    }

    public BigDecimal getTotal() {
        return total;
    }
}

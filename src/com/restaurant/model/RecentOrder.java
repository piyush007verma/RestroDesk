package com.restaurant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A short summary of an order, used in the "Recent orders" list on the dashboard. */
public class RecentOrder {

    private final int orderId;
    private final int tableNumber;
    private final String customerName;
    private final String status;
    private final BigDecimal total;
    private final LocalDateTime createdAt;

    public RecentOrder(int orderId, int tableNumber, String customerName,
                       String status, BigDecimal total, LocalDateTime createdAt) {
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.customerName = customerName;
        this.status = status;
        this.total = total;
        this.createdAt = createdAt;
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

    public String getStatus() {
        return status;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

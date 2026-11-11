package com.restaurant.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One line of the Order History list: an order of ANY status joined with its bill (if it has one).
 * {@code cleared} is true when the order was hidden with "Clear Order History" (only visible when the user asks to include cleared orders).
 * {@code billId} is 0 and {@code total}/{@code paymentMethod} are null when the order was never billed.
 * {@code paymentStatus} is PAID, UNPAID (open order, not paid yet) or N/A (cancelled, never paid).
 */
public record HistoryRow(int orderId, int tableNumber, String customerName, LocalDateTime createdAt,
                         OrderStatus status, int itemCount, int billId, BigDecimal total,
                         String paymentStatus, PaymentMethod paymentMethod, boolean cleared) {

    public boolean hasBill() {
        return billId > 0;
    }
}

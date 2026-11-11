package com.restaurant.model;

import java.time.LocalDate;

/**
 * What the user typed or chose on the Order History screen. A null field means "do not filter by this".
 * {@code includeCleared} = also list orders that were hidden with "Clear Order History" (false = normal list).
 */
public record HistoryFilter(Integer orderId, Integer tableNumber, OrderStatus status, String paymentStatus,
                            PaymentMethod method, LocalDate from, LocalDate to, boolean includeCleared) {

    public static final String PAID = "PAID";
    public static final String UNPAID = "UNPAID";

    /** The usual filter: cleared orders stay hidden. */
    public HistoryFilter(Integer orderId, Integer tableNumber, OrderStatus status, String paymentStatus,
                         PaymentMethod method, LocalDate from, LocalDate to) {
        this(orderId, tableNumber, status, paymentStatus, method, from, to, false);
    }

    public static HistoryFilter none() {
        return new HistoryFilter(null, null, null, null, null, null, null, false);
    }

    /** Same filter, but with cleared orders shown or hidden. */
    public HistoryFilter withIncludeCleared(boolean include) {
        return new HistoryFilter(orderId, tableNumber, status, paymentStatus, method, from, to, include);
    }
}

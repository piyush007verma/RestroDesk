package com.restaurant.model;

import java.math.BigDecimal;

/**
 * The summary cards. Revenue and payment counts come ONLY from bills whose payment_status is PAID
 * (one bill per order, so nothing is counted twice).
 */
public record ReportSummary(int totalOrders, int cancelledOrders, int pendingPayments, int completedPayments,
                            BigDecimal revenue, BigDecimal averageOrderValue, BigDecimal discounts, BigDecimal gst) {
}

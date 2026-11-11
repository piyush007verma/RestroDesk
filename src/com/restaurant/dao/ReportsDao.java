package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.model.MethodTotal;
import com.restaurant.model.ReportSummary;
import com.restaurant.model.SalesRow;
import com.restaurant.model.TopItem;

import java.time.LocalDate;
import java.util.List;

/**
 * READ-ONLY report queries. All dates are inclusive calendar days ({@code from} 00:00 up to the end of {@code to}).
 * Money is counted only from bills whose payment_status is PAID, by the day they were paid.
 */
public interface ReportsDao {

    ReportSummary summary(LocalDate from, LocalDate to) throws DatabaseException;

    /** One row per day that has at least one paid bill, oldest first. */
    List<SalesRow> dailySales(LocalDate from, LocalDate to) throws DatabaseException;

    /** One row per month that has at least one paid bill, oldest first. */
    List<SalesRow> monthlySales(LocalDate from, LocalDate to) throws DatabaseException;

    /** Most sold dishes of PAID orders, by quantity. */
    List<TopItem> topItems(LocalDate from, LocalDate to, int limit) throws DatabaseException;

    /** Cash, UPI and Card (all three always present, zero when unused). */
    List<MethodTotal> paymentMethods(LocalDate from, LocalDate to) throws DatabaseException;

    /** The latest saved reset (resetAt, bills and amount it stopped counting), or null when revenue was never reset. */
    com.restaurant.model.RevenueReset latestReset() throws DatabaseException;

    /** What a reset would do right now (resetAt is null): bills and money currently counted. Changes nothing. */
    com.restaurant.model.RevenueReset previewReset() throws DatabaseException;

    /** Saves a new reset (one INSERT in a transaction). No bill, order or payment is changed or deleted. */
    com.restaurant.model.RevenueReset resetRevenue() throws DatabaseException;
}

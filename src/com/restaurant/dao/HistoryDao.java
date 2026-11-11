package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.model.HistoryFilter;
import com.restaurant.model.HistoryRow;

import java.util.List;

/**
 * Access to old orders. Searching is read-only. The ONLY write is {@link #clearClosedOrders()}, which sets a time stamp
 * in orders.history_cleared_at; there is deliberately no DELETE anywhere, so bills, payments, order items and reports
 * can never be lost through this class.
 */
public interface HistoryDao {

    /** Orders of every status that match the filter, newest first, at most {@code limit} rows. */
    List<HistoryRow> search(HistoryFilter filter, int limit) throws DatabaseException;

    /** True when the database has the orders.history_cleared_at column (migration 003 was run). */
    boolean isClearingAvailable() throws DatabaseException;

    /** How many finished (PAID or CANCELLED) orders are still listed, i.e. how many a clear would hide. */
    int countClearableOrders() throws DatabaseException;

    /**
     * Hides every finished (PAID or CANCELLED) order from Order History by setting history_cleared_at, in one transaction.
     * Open orders are never touched. Returns how many orders were hidden. Nothing is deleted or changed except that column.
     */
    int clearClosedOrders() throws DatabaseException;
}

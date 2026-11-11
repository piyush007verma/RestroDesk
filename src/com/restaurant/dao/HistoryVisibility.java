package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ONE shared rule for "is this order visible in history-style lists?". Every screen that lists past or recent
 * orders (Order History, Dashboard Recent Orders, Dashboard order count) asks THIS class, so clearing the history
 * affects all of them in the same way and they can never disagree.
 * <p>
 * An order is hidden when {@code orders.history_cleared_at} has a value (set by Clear Order History, migration 003).
 * Money screens (Reports, Billing, revenue) deliberately do NOT use this rule: bills and payments always stay counted.
 * Before migration 003 the column does not exist, so nothing is hidden and the app works as before.
 */
public final class HistoryVisibility {

    private static final String COLUMN_SQL = "SELECT COUNT(*) FROM information_schema.columns "
            + "WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'history_cleared_at'";

    /** Once the column exists it never goes away, so a positive answer is remembered (a negative one is re-checked). */
    private static volatile boolean knownToExist;

    private HistoryVisibility() {
    }

    /** True when migration 003 has been applied to the database behind this connection. */
    public static boolean columnExists(Connection con) throws SQLException {
        if (knownToExist) {
            return true;
        }
        try (PreparedStatement ps = con.prepareStatement(COLUMN_SQL); ResultSet rs = ps.executeQuery()) {
            boolean exists = rs.next() && rs.getInt(1) > 0;
            knownToExist = exists;
            return exists;
        }
    }

    /**
     * SQL text to add to a WHERE clause ("AND o.history_cleared_at IS NULL"), or "" before the migration.
     * {@code alias} is a constant table alias written by the DAO (never user input).
     */
    public static String visibleOnly(Connection con, String alias) throws SQLException {
        return columnExists(con) ? " AND " + alias + ".history_cleared_at IS NULL" : "";
    }
}

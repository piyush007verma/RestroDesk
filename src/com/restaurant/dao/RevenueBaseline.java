package com.restaurant.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * ONE shared rule for "which bills count as revenue now?": only bills newer than the latest row of {@code revenue_resets}
 * (migration 004). Reports and the Dashboard both use this text, so they can never disagree.
 * Bills are never deleted or changed by a reset; before migration 004 (no table) every bill counts, as before.
 */
public final class RevenueBaseline {

    private static final String TABLE_SQL = "SELECT COUNT(*) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND table_name = 'revenue_resets'";

    /** Once the table exists it stays, so a positive answer is remembered; a negative one is checked again next time. */
    private static volatile boolean knownToExist;

    private RevenueBaseline() {
    }

    public static boolean tableExists(Connection con) throws SQLException {
        if (knownToExist) {
            return true;
        }
        try (PreparedStatement ps = con.prepareStatement(TABLE_SQL); ResultSet rs = ps.executeQuery()) {
            boolean exists = rs.next() && rs.getInt(1) > 0;
            knownToExist = exists;
            return exists;
        }
    }

    /** Text to add to a WHERE clause, e.g. " AND b.bill_id > COALESCE((SELECT ...), 0)", or "" before the migration. */
    public static String countedBillsOnly(Connection con, String billAlias) throws SQLException {
        return tableExists(con)
                ? " AND " + billAlias + ".bill_id > COALESCE((SELECT r.last_bill_id FROM revenue_resets r "
                + "ORDER BY r.reset_id DESC LIMIT 1), 0)"
                : "";
    }
}

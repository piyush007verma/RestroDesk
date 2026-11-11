package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.model.MethodTotal;
import com.restaurant.model.RevenueReset;
import com.restaurant.model.PaymentMethod;
import com.restaurant.model.ReportSummary;
import com.restaurant.model.SalesRow;
import com.restaurant.model.TopItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JDBC implementation of {@link ReportsDao}. The database does the adding up (SUM, COUNT, AVG, GROUP BY);
 * Java only reads the finished numbers. "Money" always means bills with payment_status = 'PAID'.
 */
public class ReportsDaoImpl implements ReportsDao {

    private static final String PAID_IN_PERIOD = "b.payment_status = 'PAID' AND b.paid_at >= ? AND b.paid_at < ? ";

    /** Paid bills of the period that are still counted (newer than the latest revenue reset). Parameters: from, to. */
    private static String counted(Connection con) throws SQLException {
        return PAID_IN_PERIOD + RevenueBaseline.countedBillsOnly(con, "b") + " ";
    }

    @Override
    public ReportSummary summary(LocalDate from, LocalDate to) throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            String billSql = "SELECT COUNT(*), COALESCE(SUM(b.total_amount), 0), COALESCE(AVG(b.total_amount), 0), "
                    + "COALESCE(SUM(b.discount_amount), 0), COALESCE(SUM(b.gst_amount), 0) "
                    + "FROM bills b WHERE " + counted(con);
            int total;
            int cancelled;
            int pending;
            // order counts follow the same shared rule as Order History and the Dashboard: orders cleared from the history are not counted
            String orderSql = "SELECT COUNT(*), "
                    + "COALESCE(SUM(CASE WHEN o.status = 'CANCELLED' THEN 1 ELSE 0 END), 0), "
                    + "COALESCE(SUM(CASE WHEN o.status = 'SERVED' THEN 1 ELSE 0 END), 0) "
                    + "FROM orders o WHERE o.created_at >= ? AND o.created_at < ?" + HistoryVisibility.visibleOnly(con, "o");
            try (PreparedStatement ps = con.prepareStatement(orderSql)) {
                bindPeriod(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getInt(1);
                    cancelled = rs.getInt(2);
                    pending = rs.getInt(3);
                }
            }
            try (PreparedStatement ps = con.prepareStatement(billSql)) {
                bindPeriod(ps, from, to);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new ReportSummary(total, cancelled, pending, rs.getInt(1), rs.getBigDecimal(2),
                            rs.getBigDecimal(3).setScale(2, RoundingMode.HALF_UP), rs.getBigDecimal(4), rs.getBigDecimal(5));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not calculate the report summary: " + e.getMessage(), e);
        }
    }

    @Override
    public List<SalesRow> dailySales(LocalDate from, LocalDate to) throws DatabaseException {
        return sales("DATE(b.paid_at)", from, to, "Could not load the daily sales: ");
    }

    @Override
    public List<SalesRow> monthlySales(LocalDate from, LocalDate to) throws DatabaseException {
        return sales("DATE_FORMAT(b.paid_at, '%Y-%m-01')", from, to, "Could not load the monthly sales: ");
    }

    /** {@code groupExpr} yields a date string like 2026-11-07 (day) or 2026-11-01 (month). */
    private List<SalesRow> sales(String groupExpr, LocalDate from, LocalDate to, String errorText) throws DatabaseException {
        List<SalesRow> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT " + groupExpr
                     + " AS period, COUNT(*), SUM(b.total_amount), SUM(b.discount_amount), SUM(b.gst_amount) "
                     + "FROM bills b WHERE " + counted(con) + "GROUP BY " + groupExpr + " ORDER BY period")) {
            bindPeriod(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new SalesRow(LocalDate.parse(rs.getString(1)), rs.getInt(2), rs.getBigDecimal(3),
                            rs.getBigDecimal(4), rs.getBigDecimal(5)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(errorText + e.getMessage(), e);
        }
        return rows;
    }

    @Override
    public List<TopItem> topItems(LocalDate from, LocalDate to, int limit) throws DatabaseException {
        // names and prices come from the order lines (unit_price was copied at order time); the menu row is only used for the name
        List<TopItem> items = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT m.name, SUM(oi.quantity) AS qty, SUM(oi.quantity * oi.unit_price) AS sales "
                     + "FROM order_items oi "
                     + "JOIN bills b ON b.order_id = oi.order_id "
                     + "JOIN menu_items m ON m.item_id = oi.item_id "
                     + "WHERE " + counted(con)
                     + "GROUP BY oi.item_id, m.name ORDER BY qty DESC, sales DESC, m.name LIMIT ?")) {
            bindPeriod(ps, from, to);
            ps.setInt(3, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new TopItem(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the best-selling items: " + e.getMessage(), e);
        }
        return items;
    }

    @Override
    public List<MethodTotal> paymentMethods(LocalDate from, LocalDate to) throws DatabaseException {
        Map<PaymentMethod, MethodTotal> found = new EnumMap<>(PaymentMethod.class);
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT b.payment_method, COUNT(*), SUM(b.total_amount) FROM bills b WHERE "
                     + counted(con) + "GROUP BY b.payment_method")) {
            bindPeriod(ps, from, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PaymentMethod m = PaymentMethod.valueOf(rs.getString(1));
                    found.put(m, new MethodTotal(m, rs.getInt(2), rs.getBigDecimal(3)));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the payment summary: " + e.getMessage(), e);
        }
        List<MethodTotal> all = new ArrayList<>();
        for (PaymentMethod m : PaymentMethod.values()) {
            all.add(found.getOrDefault(m, new MethodTotal(m, 0, BigDecimal.ZERO.setScale(2))));
        }
        return all;
    }

    /** Parameters 1 and 2 = start of the first day (included) and start of the day after the last day (excluded). */
    private static void bindPeriod(PreparedStatement ps, LocalDate from, LocalDate to) throws SQLException {
        ps.setTimestamp(1, Timestamp.valueOf(from.atStartOfDay()));
        ps.setTimestamp(2, Timestamp.valueOf(to.plusDays(1).atTime(LocalTime.MIDNIGHT)));
    }

    // ---------- revenue reset (migration 004) ----------

    private static DatabaseException migrationNeeded() {
        return new DatabaseException("One-time database update needed: run database/migrations/004_add_revenue_resets.sql "
                + "in MySQL Workbench (File > Open SQL Script > lightning button). It only adds one new empty table and keeps all your data.");
    }

    @Override
    public RevenueReset latestReset() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!RevenueBaseline.tableExists(con)) {
                return null;
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT reset_at, bills_cleared, amount_cleared FROM revenue_resets "
                    + "ORDER BY reset_id DESC LIMIT 1");
                 ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new RevenueReset(rs.getTimestamp(1).toLocalDateTime(), rs.getInt(2), rs.getBigDecimal(3)) : null;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not read the last revenue reset: " + e.getMessage(), e);
        }
    }

    /** Paid bills that are counted right now: how many and how much. */
    private static RevenueReset currentTotals(Connection con) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*), COALESCE(SUM(b.total_amount), 0), 0 "
                + "FROM bills b WHERE b.payment_status = 'PAID'" + RevenueBaseline.countedBillsOnly(con, "b"));
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new RevenueReset(null, rs.getInt(1), rs.getBigDecimal(2));
        }
    }

    @Override
    public RevenueReset previewReset() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!RevenueBaseline.tableExists(con)) {
                throw migrationNeeded();
            }
            return currentTotals(con);
        } catch (SQLException e) {
            throw new DatabaseException("Could not check the revenue: " + e.getMessage(), e);
        }
    }

    @Override
    public RevenueReset resetRevenue() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!RevenueBaseline.tableExists(con)) {
                throw migrationNeeded();
            }
            con.setAutoCommit(false);
            try {
                RevenueReset before = currentTotals(con);
                int lastBillId;
                try (PreparedStatement ps = con.prepareStatement("SELECT COALESCE(MAX(bill_id), 0) FROM bills");
                     ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    lastBillId = rs.getInt(1);
                }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO revenue_resets (last_bill_id, bills_cleared, amount_cleared) VALUES (?, ?, ?)")) {
                    ps.setInt(1, lastBillId);
                    ps.setInt(2, before.billsCleared());
                    ps.setBigDecimal(3, before.amountCleared());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
            return latestReset();
        } catch (SQLException e) {
            throw new DatabaseException("Revenue could not be reset (nothing was changed): " + e.getMessage(), e);
        }
    }
}

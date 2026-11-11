package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.model.HistoryFilter;
import com.restaurant.model.HistoryRow;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.PaymentMethod;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link HistoryDao}: one SELECT with JOINs, filters added as PreparedStatement parameters.
 * The only statement that writes is the UPDATE in {@link #clearClosedOrders()}; it touches one column of one table.
 */
public class HistoryDaoImpl implements HistoryDao {


    /** Checks (on an already open connection) whether migration 003 has been run. */
    private static boolean hasClearedColumn(Connection con) throws SQLException {
        return HistoryVisibility.columnExists(con); // the same shared rule the Dashboard uses
    }

    private static DatabaseException migrationNeeded() {
        return new DatabaseException("One-time database update needed: run database/migrations/003_add_history_cleared_flag.sql "
                + "in MySQL Workbench (File > Open SQL Script > lightning button). It only adds one column and keeps all your data.");
    }

    @Override
    public boolean isClearingAvailable() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            return hasClearedColumn(con);
        } catch (SQLException e) {
            throw new DatabaseException("Could not check the database: " + e.getMessage(), e);
        }
    }

    @Override
    public int countClearableOrders() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!hasClearedColumn(con)) {
                throw migrationNeeded();
            }
            // OrderStatus.closedSqlList() is a constant text ('PAID', 'CANCELLED'), never user input
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM orders WHERE status IN ("
                    + OrderStatus.closedSqlList() + ") AND history_cleared_at IS NULL");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not count the orders that can be cleared: " + e.getMessage(), e);
        }
    }

    @Override
    public int clearClosedOrders() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection()) {
            if (!hasClearedColumn(con)) {
                throw migrationNeeded();
            }
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement("UPDATE orders SET history_cleared_at = NOW() WHERE status IN ("
                    + OrderStatus.closedSqlList() + ") AND history_cleared_at IS NULL")) {
                int hidden = ps.executeUpdate();
                con.commit();
                return hidden;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DatabaseException("Order History could not be cleared (nothing was changed): " + e.getMessage(), e);
        }
    }

    @Override
    public List<HistoryRow> search(HistoryFilter f, int limit) throws DatabaseException {
        List<HistoryRow> rows = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            // before migration 003 there is no column: every order is listed and none is "cleared"
            boolean canClear = hasClearedColumn(con);
            StringBuilder sql = new StringBuilder(
                    "SELECT o.order_id, t.table_number, o.customer_name, o.created_at, o.status, "
                            + "(SELECT COALESCE(SUM(oi.quantity), 0) FROM order_items oi WHERE oi.order_id = o.order_id) AS item_count, "
                            + "b.bill_id, b.total_amount, b.payment_status, b.payment_method, "
                            + (canClear ? "(o.history_cleared_at IS NOT NULL) AS cleared " : "0 AS cleared ")
                            + "FROM orders o "
                            + "JOIN restaurant_tables t ON t.table_id = o.table_id "
                            + "LEFT JOIN bills b ON b.order_id = o.order_id "
                            + "WHERE 1 = 1 ");
            List<Object> params = new ArrayList<>();
            appendFilters(sql, params, f, canClear, limit);
            try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        rows.add(read(rs));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the order history: " + e.getMessage(), e);
        }
        return rows;
    }

    private static void appendFilters(StringBuilder sql, List<Object> params, HistoryFilter f, boolean canClear, int limit) {
        if (canClear && !f.includeCleared()) {
            sql.append("AND o.history_cleared_at IS NULL ");
        }
        if (f.orderId() != null) {
            sql.append("AND o.order_id = ? ");
            params.add(f.orderId());
        }
        if (f.tableNumber() != null) {
            sql.append("AND t.table_number = ? ");
            params.add(f.tableNumber());
        }
        if (f.status() != null) {
            sql.append("AND o.status = ? ");
            params.add(f.status().name());
        }
        if (HistoryFilter.PAID.equals(f.paymentStatus())) {
            sql.append("AND b.payment_status = 'PAID' ");
        } else if (HistoryFilter.UNPAID.equals(f.paymentStatus())) {
            sql.append("AND (b.bill_id IS NULL OR b.payment_status <> 'PAID') AND o.status <> 'CANCELLED' ");
        }
        if (f.method() != null) {
            sql.append("AND b.payment_status = 'PAID' AND b.payment_method = ? ");
            params.add(f.method().name());
        }
        if (f.from() != null) {
            sql.append("AND o.created_at >= ? ");
            params.add(Timestamp.valueOf(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            sql.append("AND o.created_at < ? ");
            params.add(Timestamp.valueOf(f.to().plusDays(1).atTime(LocalTime.MIDNIGHT)));
        }
        sql.append("ORDER BY o.order_id DESC LIMIT ?");
        params.add(limit);
    }

    private static HistoryRow read(ResultSet rs) throws SQLException {
        int billId = rs.getInt("bill_id");
        boolean billed = !rs.wasNull();
        BigDecimal total = billed ? rs.getBigDecimal("total_amount") : null;
        String billStatus = rs.getString("payment_status");
        OrderStatus status = OrderStatus.valueOf(rs.getString("status"));
        String paymentStatus;
        if (billed && "PAID".equals(billStatus)) {
            paymentStatus = "PAID";
        } else if (status == OrderStatus.CANCELLED) {
            paymentStatus = "N/A";
        } else {
            paymentStatus = "UNPAID";
        }
        String method = rs.getString("payment_method");
        return new HistoryRow(rs.getInt("order_id"), rs.getInt("table_number"), rs.getString("customer_name"),
                rs.getTimestamp("created_at").toLocalDateTime(), status, rs.getInt("item_count"),
                billed ? billId : 0, total, paymentStatus,
                billed && method != null ? PaymentMethod.valueOf(method) : null, rs.getInt("cleared") != 0);
    }
}

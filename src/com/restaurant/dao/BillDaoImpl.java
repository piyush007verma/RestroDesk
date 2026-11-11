package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.Bill;
import com.restaurant.model.BillCalculation;
import com.restaurant.model.DiscountType;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.PaymentMethod;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link BillDao}: the ONLY place with SQL for bills and payments.
 * Every query uses PreparedStatement and try-with-resources.
 * <p>
 * {@link #payOrder} is the heart of Phase 4. It is ONE transaction (auto-commit off):
 * <ol>
 *   <li>lock the order row ({@code SELECT ... FOR UPDATE}), so two payment attempts take turns</li>
 *   <li>check: order exists, is SERVED, has no bill yet</li>
 *   <li>read the items again and re-calculate: the total must equal the one the cashier confirmed</li>
 *   <li>INSERT the bill (payment status PAID)</li>
 *   <li>UPDATE the order to PAID (only if it is still SERVED)</li>
 *   <li>UPDATE the table to AVAILABLE (only if no OTHER open order uses that table)</li>
 *   <li>COMMIT. If anything above fails: ROLLBACK, so there is no bill, the order stays SERVED, the table stays OCCUPIED.</li>
 * </ol>
 */
public class BillDaoImpl implements BillDao {

    private static final String CLOSED = OrderStatus.closedSqlList(); // 'PAID', 'CANCELLED' (a constant, not user input)

    // ---------- reading ----------

    @Override
    public List<OrderSummary> findOrdersReadyForBilling() throws DatabaseException {
        String sql = "SELECT o.order_id, t.table_number, o.customer_name, o.customer_phone, o.status, o.created_at, "
                + "       COALESCE(SUM(oi.quantity), 0) AS item_count, "
                + "       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS order_total "
                + "FROM orders o "
                + "JOIN restaurant_tables t ON t.table_id = o.table_id "
                + "LEFT JOIN order_items oi ON oi.order_id = o.order_id "
                + "WHERE o.status = 'SERVED' "
                + "GROUP BY o.order_id, t.table_number, o.customer_name, o.customer_phone, o.status, o.created_at "
                + "ORDER BY o.created_at, o.order_id";
        List<OrderSummary> orders = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(new OrderSummary(
                        rs.getInt("order_id"),
                        rs.getInt("table_number"),
                        rs.getString("customer_name"),
                        rs.getString("customer_phone"),
                        OrderStatus.valueOf(rs.getString("status")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("item_count"),
                        rs.getBigDecimal("order_total")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the orders ready for billing: " + e.getMessage(), e);
        }
        return orders;
    }

    @Override
    public List<Bill> findRecentBills(int limit) throws DatabaseException {
        String sql = BILL_SELECT + "ORDER BY b.paid_at DESC, b.bill_id DESC LIMIT ?";
        List<Bill> bills = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    bills.add(readBill(rs, List.of()));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the paid bills: " + e.getMessage(), e);
        }
        return bills;
    }

    @Override
    public Bill findBill(int billId) throws DatabaseException {
        String sql = BILL_SELECT + "WHERE b.bill_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, billId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                int orderId = rs.getInt("order_id");
                return readBill(rs, readLines(con, orderId));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the bill: " + e.getMessage(), e);
        }
    }

    private static final String BILL_SELECT =
            "SELECT b.bill_id, b.order_id, b.subtotal, b.gst_percent, b.gst_amount, b.discount_amount, b.total_amount, "
                    + "b.payment_method, b.paid_at, b.payment_status, b.discount_type, b.discount_value, "
                    + "t.table_number, o.customer_name "
                    + "FROM bills b JOIN orders o ON o.order_id = b.order_id "
                    + "JOIN restaurant_tables t ON t.table_id = o.table_id ";

    private static Bill readBill(ResultSet rs, List<OrderLine> lines) throws SQLException {
        BillCalculation calc = BillCalculation.fromSaved(
                rs.getBigDecimal("subtotal"),
                DiscountType.valueOf(rs.getString("discount_type")),
                rs.getBigDecimal("discount_value"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("gst_percent"));
        return new Bill(rs.getInt("bill_id"), rs.getInt("order_id"), rs.getInt("table_number"),
                rs.getString("customer_name"), rs.getTimestamp("paid_at").toLocalDateTime(),
                PaymentMethod.valueOf(rs.getString("payment_method")), rs.getString("payment_status"), calc, lines);
    }

    private static List<OrderLine> readLines(Connection con, int orderId) throws SQLException {
        List<OrderLine> lines = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT oi.item_id, m.name, m.category, oi.unit_price, oi.quantity "
                        + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id "
                        + "WHERE oi.order_id = ? ORDER BY oi.order_item_id")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new OrderLine(rs.getInt("item_id"), rs.getString("name"), rs.getString("category"),
                            rs.getBigDecimal("unit_price"), rs.getInt("quantity")));
                }
            }
        }
        return lines;
    }

    // ---------- payment ----------

    @Override
    public int payOrder(int orderId, BillCalculation expected, PaymentMethod method)
            throws DatabaseException, InvalidInputException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            boolean committed = false;
            try {
                int billId = pay(con, orderId, expected, method);
                con.commit();
                committed = true;
                return billId;
            } finally {
                if (!committed) {
                    rollbackQuietly(con);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("The payment could not be saved, so nothing was changed "
                    + "(the table stays occupied): " + e.getMessage(), e);
        }
    }

    private static int pay(Connection con, int orderId, BillCalculation expected, PaymentMethod method)
            throws SQLException, InvalidInputException {
        // 1. lock the order and check it may be paid
        int tableId;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT table_id, status FROM orders WHERE order_id = ? FOR UPDATE")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InvalidInputException("This order no longer exists. The list has been refreshed.");
                }
                tableId = rs.getInt("table_id");
                String status = rs.getString("status");
                if (OrderStatus.PAID.name().equals(status)) {
                    throw new InvalidInputException("This order has already been paid. "
                            + "A second payment was not recorded.");
                }
                if (OrderStatus.CANCELLED.name().equals(status)) {
                    throw new InvalidInputException("This order was cancelled and cannot be billed.");
                }
                if (!OrderStatus.SERVED.name().equals(status)) {
                    throw new InvalidInputException("Only SERVED orders can be billed. This order is "
                            + status + ".");
                }
            }
        }
        // 2. no bill yet (also protected by UNIQUE(order_id) in the database)
        try (PreparedStatement ps = con.prepareStatement("SELECT bill_id FROM bills WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    throw new InvalidInputException("A bill already exists for this order. "
                            + "A second payment was not recorded.");
                }
            }
        }
        // 3. re-read the items and re-calculate: it must be the bill the cashier confirmed
        BigDecimal subtotal;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(quantity * unit_price), 0) FROM order_items WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                subtotal = rs.getBigDecimal(1);
            }
        }
        BillCalculation actual = BillCalculation.calculate(subtotal, expected.getDiscountType(),
                expected.getDiscountValue(), expected.getGstPercent());
        if (actual.getGrandTotal().compareTo(expected.getGrandTotal()) != 0
                || actual.getSubtotal().compareTo(expected.getSubtotal()) != 0) {
            throw new InvalidInputException("The food items of this order changed while you were billing it "
                    + "(the total is now " + actual.getGrandTotal().toPlainString() + "). "
                    + "Nothing was paid. The bill has been refreshed - please check it and pay again.");
        }
        // 4. the bill (payment status PAID)
        int billId;
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO bills (order_id, subtotal, gst_percent, gst_amount, discount_amount, total_amount, "
                        + "payment_method, payment_status, discount_type, discount_value) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PAID', ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, orderId);
            ps.setBigDecimal(2, actual.getSubtotal());
            ps.setBigDecimal(3, actual.getGstPercent());
            ps.setBigDecimal(4, actual.getGstAmount());
            ps.setBigDecimal(5, actual.getDiscountAmount());
            ps.setBigDecimal(6, actual.getGrandTotal());
            ps.setString(7, method.name());
            ps.setString(8, actual.getDiscountType().name());
            ps.setBigDecimal(9, actual.getDiscountValue());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("The database did not return the new bill number.");
                }
                billId = keys.getInt(1);
            }
        }
        // 5. the order is finished: SERVED -> PAID (the existing final status)
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE orders SET status = 'PAID' WHERE order_id = ? AND status = 'SERVED'")) {
            ps.setInt(1, orderId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("The order could not be marked as paid.");
            }
        }
        // 6. ONLY NOW free the table, and never a table that another open order is still using
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE restaurant_tables SET status = 'AVAILABLE' WHERE table_id = ? AND NOT EXISTS ("
                        + "SELECT 1 FROM orders o2 WHERE o2.table_id = ? AND o2.order_id <> ? "
                        + "AND o2.status NOT IN (" + CLOSED + "))")) {
            ps.setInt(1, tableId);
            ps.setInt(2, tableId);
            ps.setInt(3, orderId);
            ps.executeUpdate(); // 0 rows is fine: the table was already free, or another open order keeps it occupied
        }
        return billId;
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // the connection is closed right after this anyway; the original error is the one worth reporting
        }
    }
}

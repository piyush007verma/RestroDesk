package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.TableOverview;
import com.restaurant.model.TableStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link OrderDao}: the ONLY place with SQL for orders and their food items.
 * Every query uses PreparedStatement and try-with-resources.
 * <p>
 * TRANSACTIONS AND LOCKS: every change runs inside {@link #inTransaction}. Its first step is always
 * {@code SELECT ... FOR UPDATE} on the order row. That makes the database lock the order, so two computers
 * changing the same order take turns instead of overwriting each other, and the "is this order still open?"
 * check is true at the moment of the change (an order that was just paid cannot be edited any more).
 * If anything fails, the transaction is rolled back and nothing is half-saved.
 */
public class OrderDaoImpl implements OrderDao {

    private static final String CLOSED = OrderStatus.closedSqlList(); // 'PAID', 'CANCELLED' (a constant, not user input)

    // ---------- reading ----------

    @Override
    public List<OrderSummary> findActiveOrders() throws DatabaseException {
        String sql = "SELECT o.order_id, t.table_number, o.customer_name, o.customer_phone, o.status, o.created_at, "
                + "       COALESCE(SUM(oi.quantity), 0) AS item_count, "
                + "       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS order_total "
                + "FROM orders o "
                + "JOIN restaurant_tables t ON t.table_id = o.table_id "
                + "LEFT JOIN order_items oi ON oi.order_id = o.order_id "
                + "WHERE o.status NOT IN (" + CLOSED + ") "
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
                        parseStatus(rs.getString("status")),
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getInt("item_count"),
                        rs.getBigDecimal("order_total")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the orders: " + e.getMessage(), e);
        }
        return orders;
    }

    @Override
    public List<OrderLine> findLines(int orderId) throws DatabaseException {
        String sql = "SELECT oi.item_id, m.name, m.category, oi.unit_price, oi.quantity "
                + "FROM order_items oi JOIN menu_items m ON m.item_id = oi.item_id "
                + "WHERE oi.order_id = ? ORDER BY oi.order_item_id";
        List<OrderLine> lines = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new OrderLine(
                            rs.getInt("item_id"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getBigDecimal("unit_price"),
                            rs.getInt("quantity")));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the food items of the order: " + e.getMessage(), e);
        }
        return lines;
    }

    @Override
    public List<TableOverview> findSeatedTablesWithoutOrder() throws DatabaseException {
        String sql = "SELECT t.table_id, t.table_number, t.capacity FROM restaurant_tables t "
                + "WHERE t.status = 'OCCUPIED' AND NOT EXISTS ("
                + "      SELECT 1 FROM orders o WHERE o.table_id = t.table_id AND o.status NOT IN (" + CLOSED + ")) "
                + "ORDER BY t.table_number";
        List<TableOverview> tables = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(new TableOverview(rs.getInt("table_id"), rs.getInt("table_number"),
                        rs.getInt("capacity"), TableStatus.OCCUPIED, 0, null, null));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the tables: " + e.getMessage(), e);
        }
        return tables;
    }

    // ---------- changing ----------

    @Override
    public int findOrCreateOpenOrder(int tableId, String customerName) throws DatabaseException, InvalidInputException {
        return inTransaction("Could not open the order", con -> {
            // lock the table row first, so two computers cannot both create an order for it
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT status FROM restaurant_tables WHERE table_id = ? FOR UPDATE")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InvalidInputException("This table no longer exists. Please reopen the screen.");
                    }
                    if (!TableStatus.OCCUPIED.name().equals(rs.getString(1))) {
                        throw new InvalidInputException("This table is not occupied. "
                                + "Seat the customer on the Tables screen first.");
                    }
                }
            }
            // REUSE the open order if there is one (this is the normal case: seating created it)
            try (PreparedStatement ps = con.prepareStatement("SELECT order_id FROM orders WHERE table_id = ? "
                    + "AND status NOT IN (" + CLOSED + ") ORDER BY order_id DESC LIMIT 1 FOR UPDATE")) {
                ps.setInt(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            // none: create the PENDING order
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO orders (table_id, customer_name, status) VALUES (?, ?, 'PENDING')",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, tableId);
                ps.setString(2, customerName);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("The database did not return the new order id.");
                    }
                    return keys.getInt(1);
                }
            }
        });
    }

    @Override
    public void addItem(int orderId, int itemId, int quantity) throws DatabaseException, InvalidInputException {
        inTransaction("Could not add the item to the order", con -> {
            lockOpenOrder(con, orderId);

            String name;
            BigDecimal price;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT name, price, available FROM menu_items WHERE item_id = ?")) {
                ps.setInt(1, itemId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new InvalidInputException("This dish no longer exists on the menu. "
                                + "Please reopen the Orders screen.");
                    }
                    name = rs.getString("name");
                    price = rs.getBigDecimal("price");
                    if (!rs.getBoolean("available")) {
                        throw new InvalidInputException("\"" + name + "\" is not available right now, "
                                + "so it cannot be added to an order.");
                    }
                }
            }

            Integer current = lineQuantity(con, orderId, itemId);
            if (current == null) {
                tooMany(quantity, 0);
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO order_items (order_id, item_id, quantity, unit_price) VALUES (?, ?, ?, ?)")) {
                    ps.setInt(1, orderId);
                    ps.setInt(2, itemId);
                    ps.setInt(3, quantity);
                    ps.setBigDecimal(4, price); // the price is COPIED now; later menu price changes do not touch it
                    ps.executeUpdate();
                }
            } else {
                tooMany(current + quantity, current);
                setQuantity(con, orderId, itemId, current + quantity);
            }
            return null;
        });
    }

    @Override
    public void changeQuantity(int orderId, int itemId, int delta) throws DatabaseException, InvalidInputException {
        inTransaction("Could not change the quantity", con -> {
            lockOpenOrder(con, orderId);
            Integer current = lineQuantity(con, orderId, itemId);
            if (current == null) {
                throw new InvalidInputException("This item is no longer on the order. The list has been refreshed.");
            }
            int wanted = current + delta;
            if (wanted < 1) {
                throw new InvalidInputException("The quantity cannot go below 1. "
                        + "Use Remove to take the item off the order.");
            }
            tooMany(wanted, current);
            setQuantity(con, orderId, itemId, wanted);
            return null;
        });
    }

    @Override
    public void removeItem(int orderId, int itemId) throws DatabaseException, InvalidInputException {
        inTransaction("Could not remove the item", con -> {
            lockOpenOrder(con, orderId);
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM order_items WHERE order_id = ? AND item_id = ?")) {
                ps.setInt(1, orderId);
                ps.setInt(2, itemId);
                if (ps.executeUpdate() == 0) {
                    throw new InvalidInputException("This item is no longer on the order. The list has been refreshed.");
                }
            }
            return null;
        });
    }

    @Override
    public void updateStatus(int orderId, OrderStatus expectedCurrent, OrderStatus target)
            throws DatabaseException, InvalidInputException {
        inTransaction("Could not change the order status", con -> {
            OrderStatus current = lockOpenOrder(con, orderId);
            if (current != expectedCurrent) {
                throw new InvalidInputException("This order was just changed by someone else "
                        + "(it is now " + current.getLabel().toUpperCase() + "). The list has been refreshed.");
            }
            if (expectedCurrent == OrderStatus.PENDING && target != OrderStatus.PENDING && orderIsEmpty(con, orderId)) {
                throw new InvalidInputException("Add at least one food item before sending the order to the kitchen.");
            }
            try (PreparedStatement ps = con.prepareStatement("UPDATE orders SET status = ? WHERE order_id = ?")) {
                ps.setString(1, target.name());
                ps.setInt(2, orderId);
                ps.executeUpdate();
            } catch (SQLException e) {
                // MySQL and other servers word "this ENUM value does not exist" differently; recognise them all.
                String text = String.valueOf(e.getMessage()).toLowerCase();
                if (e.getErrorCode() == 1265 || text.contains("enum") || text.contains("truncated")) {
                    throw new SQLException("Data truncated: the database could not store the status "
                            + target.name() + " (" + e.getMessage() + ")", e.getSQLState(), e.getErrorCode(), e);
                }
                throw e;
            }
            // Read it back: an old database that does not know READY might store nothing useful (data truncated).
            try (PreparedStatement ps = con.prepareStatement("SELECT status FROM orders WHERE order_id = ?")) {
                ps.setInt(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next() || !target.name().equals(rs.getString(1))) {
                        throw new SQLException("Data truncated: the database did not store the status " + target.name());
                    }
                }
            }
            return null;
        });
    }

    // ---------- helpers ----------

    /** One piece of work that runs inside a transaction and uses the given connection. */
    @FunctionalInterface
    private interface TxWork<T> {
        T run(Connection con) throws SQLException, InvalidInputException;
    }

    /** Opens a connection, turns auto-commit off, runs the work, then commits; any failure rolls everything back. */
    private <T> T inTransaction(String failureText, TxWork<T> work) throws DatabaseException, InvalidInputException {
        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            boolean committed = false;
            try {
                T result = work.run(con);
                con.commit();
                committed = true;
                return result;
            } finally {
                if (!committed) {
                    rollbackQuietly(con);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException(failureText + ": " + e.getMessage(), e);
        }
    }

    /** Locks the order row and checks the order still exists and is open. Returns its current status. */
    private static OrderStatus lockOpenOrder(Connection con, int orderId) throws SQLException, InvalidInputException {
        try (PreparedStatement ps = con.prepareStatement("SELECT status FROM orders WHERE order_id = ? FOR UPDATE")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new InvalidInputException("This order no longer exists. The list has been refreshed.");
                }
                OrderStatus status = parseStatus(rs.getString(1));
                if (!status.isOpen()) {
                    throw new InvalidInputException("This order is already " + status.getLabel().toLowerCase()
                            + ", so it cannot be changed any more. The list has been refreshed.");
                }
                return status;
            }
        }
    }

    /** Current quantity of a dish on an order (row locked), or null when the dish is not on the order. */
    private static Integer lineQuantity(Connection con, int orderId, int itemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT quantity FROM order_items WHERE order_id = ? AND item_id = ? FOR UPDATE")) {
            ps.setInt(1, orderId);
            ps.setInt(2, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    private static void setQuantity(Connection con, int orderId, int itemId, int quantity) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE order_items SET quantity = ? WHERE order_id = ? AND item_id = ?")) {
            ps.setInt(1, quantity);
            ps.setInt(2, orderId);
            ps.setInt(3, itemId);
            ps.executeUpdate();
        }
    }

    private static boolean orderIsEmpty(Connection con, int orderId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(quantity), 0) FROM order_items WHERE order_id = ?")) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() || rs.getInt(1) == 0;
            }
        }
    }

    private static void tooMany(int wantedTotal, int alreadyOnOrder) throws InvalidInputException {
        if (wantedTotal > OrderLine.MAX_QUANTITY) {
            throw new InvalidInputException("You can order at most " + OrderLine.MAX_QUANTITY
                    + " of one item (this order already has " + alreadyOnOrder + ").");
        }
    }

    private static OrderStatus parseStatus(String text) throws SQLException {
        try {
            return OrderStatus.valueOf(text);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new SQLException("The database contains an unknown order status: \"" + text + "\".");
        }
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // The connection is closed right after this; the database discards uncommitted work anyway.
        }
    }
}

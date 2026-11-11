package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.TableUnavailableException;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.TableOverview;
import com.restaurant.model.TableStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link TableDao}: the ONLY place with SQL for table management.
 * <p>
 * TRANSACTIONS: assigning and releasing change two tables (restaurant_tables and orders).
 * We turn auto-commit off, do both changes, then commit. If anything fails we roll back, so the
 * database can never be left half-changed (for example "table occupied but no order").
 * <p>
 * OPEN ORDER = any order that is not PAID or CANCELLED (PENDING, PREPARING, READY, SERVED), see {@link OrderStatus}.
 * <p>
 * DUPLICATE ASSIGNMENT: the first statement is
 * {@code UPDATE ... SET status='OCCUPIED' WHERE table_id=? AND status='AVAILABLE'}.
 * The database locks that row, so if two people click at the same moment only ONE update changes a
 * row; the other sees 0 changed rows and gets a TableUnavailableException.
 */
public class TableDaoImpl implements TableDao {

    @Override
    public List<TableOverview> findAllWithCustomers() throws DatabaseException {
        String sql = "SELECT t.table_id, t.table_number, t.capacity, t.status, "
                + "       o.order_id, o.customer_name, o.customer_phone "
                + "FROM restaurant_tables t "
                + "LEFT JOIN orders o ON o.order_id = ("
                + "      SELECT MAX(o2.order_id) FROM orders o2 "
                + "      WHERE o2.table_id = t.table_id "
                + "        AND o2.status NOT IN (" + OrderStatus.closedSqlList() + ")) "
                + "ORDER BY t.table_number";
        List<TableOverview> tables = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(new TableOverview(
                        rs.getInt("table_id"),
                        rs.getInt("table_number"),
                        rs.getInt("capacity"),
                        TableStatus.valueOf(rs.getString("status")),
                        rs.getInt("order_id"),              // 0 when the LEFT JOIN found no order
                        rs.getString("customer_name"),      // null when no order
                        rs.getString("customer_phone")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the tables: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public int assignTable(int tableId, String customerName, String customerPhone)
            throws DatabaseException, TableUnavailableException {
        String claimTable = "UPDATE restaurant_tables SET status = 'OCCUPIED' "
                + "WHERE table_id = ? AND status = 'AVAILABLE'";
        String createOrder = "INSERT INTO orders (table_id, customer_name, customer_phone, status) "
                + "VALUES (?, ?, ?, 'PENDING')";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false); // start the transaction
            boolean committed = false;
            try {
                // Step 1: take the table, but only if it is still free
                try (PreparedStatement ps = con.prepareStatement(claimTable)) {
                    ps.setInt(1, tableId);
                    if (ps.executeUpdate() == 0) {
                        throw new TableUnavailableException(
                                "This table is not available any more. Someone may have just assigned it. "
                                        + "The list has been refreshed.");
                    }
                }
                // Step 2: create the customer's PENDING order on that table
                int orderId;
                try (PreparedStatement ps = con.prepareStatement(createOrder, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, tableId);
                    ps.setString(2, customerName);
                    if (customerPhone == null) {
                        ps.setNull(3, Types.VARCHAR);
                    } else {
                        ps.setString(3, customerPhone);
                    }
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("The database did not return the new order id.");
                        }
                        orderId = keys.getInt(1);
                    }
                }
                con.commit(); // both steps worked: save them together
                committed = true;
                return orderId;
            } finally {
                if (!committed) {
                    rollbackQuietly(con); // something failed: undo step 1 as well
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not assign the table: " + e.getMessage(), e);
        }
    }

    @Override
    public void releaseTable(int tableId) throws DatabaseException, TableUnavailableException {
        String freeTable = "UPDATE restaurant_tables SET status = 'AVAILABLE' "
                + "WHERE table_id = ? AND status = 'OCCUPIED'";
        String cancelOpenOrders = "UPDATE orders SET status = 'CANCELLED' "
                + "WHERE table_id = ? AND status NOT IN (" + OrderStatus.closedSqlList() + ")";

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            boolean committed = false;
            try {
                try (PreparedStatement ps = con.prepareStatement(freeTable)) {
                    ps.setInt(1, tableId);
                    if (ps.executeUpdate() == 0) {
                        throw new TableUnavailableException(
                                "This table is not occupied (it may already have been released). "
                                        + "The list has been refreshed.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(cancelOpenOrders)) {
                    ps.setInt(1, tableId);
                    ps.executeUpdate(); // 0 rows is fine: the table may have had no open order
                }
                con.commit();
                committed = true;
            } finally {
                if (!committed) {
                    rollbackQuietly(con);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not release the table: " + e.getMessage(), e);
        }
    }

    private static void rollbackQuietly(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
            // The connection is about to be closed anyway; the database discards uncommitted work.
        }
    }
}

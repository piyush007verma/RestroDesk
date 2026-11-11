package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.model.RecentOrder;
import com.restaurant.model.RestaurantTable;
import com.restaurant.model.TableStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** JDBC implementation of {@link DashboardDao}. Uses PreparedStatement everywhere. */
public class DashboardDaoImpl implements DashboardDao {

    @Override
    public int countOrders() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM orders o WHERE 1 = 1"
                     + HistoryVisibility.visibleOnly(con, "o")); // cleared orders are not counted (shared rule)
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DatabaseException("Could not count orders: " + e.getMessage(), e);
        }
    }

    @Override
    public BigDecimal totalRevenue() throws DatabaseException {
        try (Connection con = DatabaseConnection.getConnection();
             // same shared rule as Reports: only bills newer than the latest revenue reset are counted
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(b.total_amount), 0) FROM bills b WHERE 1 = 1"
                     + RevenueBaseline.countedBillsOnly(con, "b"));
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (SQLException e) {
            throw new DatabaseException("Could not calculate revenue: " + e.getMessage(), e);
        }
    }

    @Override
    public List<RestaurantTable> findAllTables() throws DatabaseException {
        String sql = "SELECT table_id, table_number, capacity, status "
                + "FROM restaurant_tables ORDER BY table_number";
        List<RestaurantTable> tables = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                tables.add(new RestaurantTable(
                        rs.getInt("table_id"),
                        rs.getInt("table_number"),
                        rs.getInt("capacity"),
                        TableStatus.valueOf(rs.getString("status"))));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load tables: " + e.getMessage(), e);
        }
        return tables;
    }

    @Override
    public List<RecentOrder> findRecentOrders(int limit) throws DatabaseException {
        List<RecentOrder> orders = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection()) {
            // the shared rule hides orders cleared from the history (same rule as Order History)
            String sql = "SELECT o.order_id, t.table_number, o.customer_name, o.status, o.created_at, "
                    + "       COALESCE(SUM(oi.quantity * oi.unit_price), 0) AS order_total "
                    + "FROM orders o "
                    + "JOIN restaurant_tables t ON t.table_id = o.table_id "
                    + "LEFT JOIN order_items oi ON oi.order_id = o.order_id "
                    + "WHERE 1 = 1" + HistoryVisibility.visibleOnly(con, "o") + " "
                    + "GROUP BY o.order_id, t.table_number, o.customer_name, o.status, o.created_at "
                    + "ORDER BY o.created_at DESC, o.order_id DESC "
                    + "LIMIT ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Timestamp created = rs.getTimestamp("created_at");
                        orders.add(new RecentOrder(
                                rs.getInt("order_id"),
                                rs.getInt("table_number"),
                                rs.getString("customer_name"),
                                rs.getString("status"),
                                rs.getBigDecimal("order_total"),
                                created.toLocalDateTime()));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load recent orders: " + e.getMessage(), e);
        }
        return orders;
    }
}

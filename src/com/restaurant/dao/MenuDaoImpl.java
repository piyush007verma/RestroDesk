package com.restaurant.dao;

import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MenuItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * JDBC implementation of {@link MenuDao}: the ONLY place with SQL for menu items.
 * Every query uses PreparedStatement and try-with-resources (connections close themselves).
 */
public class MenuDaoImpl implements MenuDao {

    @Override
    public List<MenuItem> findAll() throws DatabaseException {
        String sql = "SELECT item_id, name, category, price, available "
                + "FROM menu_items ORDER BY category, name";
        List<MenuItem> items = new ArrayList<>();
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(new MenuItem(
                        rs.getInt("item_id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getBigDecimal("price"),
                        rs.getBoolean("available")));
            }
        } catch (SQLException e) {
            throw new DatabaseException("Could not load the menu: " + e.getMessage(), e);
        }
        return items;
    }

    @Override
    public int insert(MenuItem item) throws DatabaseException, InvalidInputException {
        String sql = "INSERT INTO menu_items (name, category, price, available) VALUES (?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getCategory());
            ps.setBigDecimal(3, item.getPrice());
            ps.setBoolean(4, item.isAvailable());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getInt(1) : 0;
            }
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new InvalidInputException("A menu item named \"" + item.getName()
                        + "\" already exists. Please use a different name.");
            }
            throw new DatabaseException("Could not add the menu item: " + e.getMessage(), e);
        }
    }

    @Override
    public void update(MenuItem item) throws DatabaseException, InvalidInputException {
        String sql = "UPDATE menu_items SET name = ?, category = ?, price = ?, available = ? "
                + "WHERE item_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, item.getName());
            ps.setString(2, item.getCategory());
            ps.setBigDecimal(3, item.getPrice());
            ps.setBoolean(4, item.isAvailable());
            ps.setInt(5, item.getId());
            int changed = ps.executeUpdate();
            // Some MySQL settings report 0 when nothing actually changed, so check the item still exists.
            if (changed == 0 && !exists(con, item.getId())) {
                throw new InvalidInputException("This item no longer exists. Please refresh the menu.");
            }
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                throw new InvalidInputException("Another menu item is already named \"" + item.getName()
                        + "\". Please use a different name.");
            }
            throw new DatabaseException("Could not update the menu item: " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(int itemId) throws DatabaseException, InvalidInputException {
        String sql = "DELETE FROM menu_items WHERE item_id = ?";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, itemId);
            if (ps.executeUpdate() == 0) {
                throw new InvalidInputException("This item no longer exists. Please refresh the menu.");
            }
        } catch (SQLException e) {
            if (isConstraintViolation(e)) {
                // order_items points to this dish, so the database refuses to delete it
                throw new InvalidInputException("This item is part of existing orders, so it cannot be deleted.\n"
                        + "Untick \"Available\" instead to hide it from new orders.");
            }
            throw new DatabaseException("Could not delete the menu item: " + e.getMessage(), e);
        }
    }

    private static boolean exists(Connection con, int itemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT 1 FROM menu_items WHERE item_id = ?")) {
            ps.setInt(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** SQLState codes starting with "23" mean "an integrity rule was broken" (duplicate, foreign key). */
    private static boolean isConstraintViolation(SQLException e) {
        return e.getSQLState() != null && e.getSQLState().startsWith("23");
    }
}

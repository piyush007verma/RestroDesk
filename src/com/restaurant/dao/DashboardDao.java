package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.model.RecentOrder;
import com.restaurant.model.RestaurantTable;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read-only database queries needed by the dashboard.
 * It is an interface so the service layer does not care HOW the data is fetched
 * (JDBC today, something else tomorrow) - this is abstraction in action.
 */
public interface DashboardDao {

    int countOrders() throws DatabaseException;

    BigDecimal totalRevenue() throws DatabaseException;

    List<RestaurantTable> findAllTables() throws DatabaseException;

    List<RecentOrder> findRecentOrders(int limit) throws DatabaseException;
}

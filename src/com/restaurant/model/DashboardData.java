package com.restaurant.model;

import java.math.BigDecimal;
import java.util.List;

/** Everything the dashboard needs to draw itself, bundled in one object. */
public class DashboardData {

    private final int totalOrders;
    private final BigDecimal totalRevenue;
    private final int availableTables;
    private final int occupiedTables;
    private final List<RestaurantTable> tables;
    private final List<RecentOrder> recentOrders;
    private final boolean demoMode;
    private final String notice;

    public DashboardData(int totalOrders, BigDecimal totalRevenue, int availableTables,
                         int occupiedTables, List<RestaurantTable> tables,
                         List<RecentOrder> recentOrders, boolean demoMode, String notice) {
        this.totalOrders = totalOrders;
        this.totalRevenue = totalRevenue;
        this.availableTables = availableTables;
        this.occupiedTables = occupiedTables;
        this.tables = tables;
        this.recentOrders = recentOrders;
        this.demoMode = demoMode;
        this.notice = notice;
    }

    public int getTotalOrders() {
        return totalOrders;
    }

    public BigDecimal getTotalRevenue() {
        return totalRevenue;
    }

    public int getAvailableTables() {
        return availableTables;
    }

    public int getOccupiedTables() {
        return occupiedTables;
    }

    public List<RestaurantTable> getTables() {
        return tables;
    }

    public List<RecentOrder> getRecentOrders() {
        return recentOrders;
    }

    /** True when the numbers are sample data because MySQL is not connected. */
    public boolean isDemoMode() {
        return demoMode;
    }

    public String getNotice() {
        return notice;
    }
}

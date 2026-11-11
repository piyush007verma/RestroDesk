package com.restaurant.service;

import com.restaurant.dao.DashboardDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.model.DashboardData;
import com.restaurant.model.RecentOrder;
import com.restaurant.model.RestaurantTable;
import com.restaurant.model.TableStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Business logic for the dashboard. The Swing screen asks this class for data;
 * this class asks the DAO. If MySQL is not reachable, it returns clearly
 * labelled demo data so the app still opens (very handy while the team is
 * still setting up databases).
 */
public class DashboardService {

    private final DashboardDao dao;

    public DashboardService(DashboardDao dao) {
        this.dao = dao;
    }

    public DashboardData loadDashboard() {
        try {
            List<RestaurantTable> tables = dao.findAllTables();
            // Stream + lambda: count tables by status
            int available = (int) tables.stream()
                    .filter(t -> t.getStatus() == TableStatus.AVAILABLE).count();
            int occupied = tables.size() - available;

            return new DashboardData(
                    dao.countOrders(),
                    dao.totalRevenue(),
                    available,
                    occupied,
                    tables,
                    dao.findRecentOrders(6),
                    false,
                    null);
        } catch (DatabaseException e) {
            return demoData(e.getMessage());
        }
    }

    private DashboardData demoData(String reason) {
        List<RestaurantTable> tables = List.of(
                new RestaurantTable(1, 1, 2, TableStatus.OCCUPIED),
                new RestaurantTable(2, 2, 2, TableStatus.AVAILABLE),
                new RestaurantTable(3, 3, 4, TableStatus.OCCUPIED),
                new RestaurantTable(4, 4, 4, TableStatus.AVAILABLE),
                new RestaurantTable(5, 5, 4, TableStatus.AVAILABLE),
                new RestaurantTable(6, 6, 6, TableStatus.OCCUPIED),
                new RestaurantTable(7, 7, 6, TableStatus.AVAILABLE),
                new RestaurantTable(8, 8, 8, TableStatus.AVAILABLE),
                new RestaurantTable(9, 9, 2, TableStatus.AVAILABLE));

        LocalDateTime now = LocalDateTime.now();
        List<RecentOrder> orders = List.of(
                new RecentOrder(1042, 3, "Aarav Mehta", "PREPARING", new BigDecimal("1280.00"), now.minusMinutes(8)),
                new RecentOrder(1041, 6, "Neha Kapoor", "SERVED", new BigDecimal("2450.00"), now.minusMinutes(26)),
                new RecentOrder(1040, 1, "Rohan Verma", "PENDING", new BigDecimal("640.00"), now.minusMinutes(31)),
                new RecentOrder(1039, 5, "Sara Khan", "PAID", new BigDecimal("1890.00"), now.minusHours(1)),
                new RecentOrder(1038, 2, "Vikram Singh", "PAID", new BigDecimal("960.00"), now.minusHours(2)),
                new RecentOrder(1037, 7, "Isha Gupta", "CANCELLED", new BigDecimal("0.00"), now.minusHours(3)));

        return new DashboardData(
                128,
                new BigDecimal("84620.00"),
                6,
                3,
                tables,
                orders,
                true,
                "Demo data shown - MySQL is not connected. (" + reason + ")");
    }
}

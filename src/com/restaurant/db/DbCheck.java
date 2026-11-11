package com.restaurant.db;

import com.restaurant.exception.DatabaseException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * A small helper program (NOT part of the app window) that tests the database connection one step at a time
 * and tells you which step fails and how to fix it. It never prints your password.
 * <p>
 * Run it with check-db.bat (Windows) or ./check-db.sh (Linux / macOS / Git Bash).
 */
public final class DbCheck {

    /**
     * Every table and the columns the DAO classes use (copied from the SQL in MenuDaoImpl, TableDaoImpl and
     * DashboardDaoImpl, plus the columns of the later milestones). LinkedHashMap keeps this order.
     */
    private static final Map<String, String> TABLES = new LinkedHashMap<>();

    static {
        TABLES.put("restaurant_tables", "table_id, table_number, capacity, status");
        TABLES.put("menu_items", "item_id, name, category, price, available");
        TABLES.put("orders", "order_id, table_id, customer_name, customer_phone, status, created_at");
        TABLES.put("order_items", "order_item_id, order_id, item_id, quantity, unit_price");
        TABLES.put("bills", "bill_id, order_id, subtotal, gst_percent, gst_amount, discount_amount, "
                + "total_amount, payment_method, paid_at");
    }

    private DbCheck() {
    }

    public static void main(String[] args) {
        System.out.println("RestoDesk database check");
        System.out.println("========================");
        boolean allOk = run();
        System.out.println();
        System.out.println(allOk
                ? "RESULT: everything is OK. Start the app with run.bat / run.sh."
                : "RESULT: NOT OK. Fix the first [FAIL] line above, then run this check again.");
        System.exit(allOk ? 0 : 1);
    }

    /** Runs every step in order and stops at the first failure (later steps need the earlier ones). */
    private static boolean run() {
        ok("Java " + System.getProperty("java.version") + " (Java 21 is required)");
        System.out.println("      working folder: " + Path.of("").toAbsolutePath());

        // 1. settings file
        Properties props;
        try {
            Path file = DatabaseConnection.findConfigFile();
            ok("settings file found: " + file);
            props = DatabaseConnection.loadConfig();
        } catch (DatabaseException e) {
            return fail("settings file", e.getMessage());
        }
        String url = props.getProperty("db.url");
        System.out.println("      db.url      = " + url);
        System.out.println("      db.user     = " + props.getProperty("db.user"));
        String pw = props.getProperty("db.password", "");
        System.out.println("      db.password = " + (pw.isEmpty() ? "(empty)" : "(set, hidden)"));
        try {
            DatabaseConnection.checkPassword(props);
            ok("password was changed from the example text");
        } catch (DatabaseException e) {
            return fail("password", e.getMessage());
        }

        // 2. driver jar
        try {
            DatabaseConnection.requireDriver(url);
            if (url.startsWith("jdbc:mysql:")) {
                Class<?> driver = Class.forName("com.mysql.cj.jdbc.Driver");
                java.security.CodeSource source = driver.getProtectionDomain().getCodeSource();
                ok("MySQL driver found" + (source == null ? "" : ": " + source.getLocation()));
            } else {
                ok("db.url is not a MySQL address; driver check skipped");
            }
        } catch (DatabaseException e) {
            return fail("MySQL driver (jar in lib folder)", e.getMessage());
        } catch (ClassNotFoundException e) {
            return fail("MySQL driver (jar in lib folder)", "Driver class not found: " + e.getMessage());
        }

        // 3. connection + 4. tables (same connection)
        try (Connection con = DatabaseConnection.getConnection()) {
            ok("connected to " + con.getMetaData().getDatabaseProductName() + " "
                    + con.getMetaData().getDatabaseProductVersion() + ", database \"" + con.getCatalog() + "\"");
            boolean tablesOk = checkTables(con);
            // the status check needs the orders table to exist, so it only runs when the tables are fine
            boolean statusOk = !tablesOk || checkReadyStatus(con);
            boolean billingOk = !tablesOk || checkBillingColumns(con);
            boolean clearOk = !tablesOk || checkClearHistoryColumn(con);
            boolean resetOk = !tablesOk || checkRevenueResets(con);
            return tablesOk && statusOk && billingOk && clearOk && resetOk;
        } catch (DatabaseException e) {
            return fail("connection to MySQL", e.getMessage());
        } catch (SQLException e) {
            return fail("connection to MySQL", DatabaseConnection.explain(e));
        }
    }

    /** For each table: first check that every expected column exists, then count the rows. */
    private static boolean checkTables(Connection con) {
        boolean good = true;
        for (Map.Entry<String, String> entry : TABLES.entrySet()) {
            String table = entry.getKey();
            // table and column names come from the constant map above, never from user input
            try (PreparedStatement ps = con.prepareStatement("SELECT " + entry.getValue() + " FROM " + table + " LIMIT 0")) {
                ps.executeQuery().close();
            } catch (SQLException e) {
                good = false;
                boolean missingTable = "42S02".equals(e.getSQLState()) || e.getErrorCode() == 1146;
                fail("table " + table, (missingTable
                        ? "The table does not exist. FIX: run database/schema.sql in MySQL Workbench (README, step 2)."
                        : "The table exists but does not have the columns the program needs (" + entry.getValue() + "). "
                        + "FIX: it is an old or half-made table - start over with an empty database (README, section 'Start over').")
                        + " [Technical detail: " + e.getMessage() + "]");
                continue;
            }
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM " + table);
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                ok("table " + table + ": columns correct, " + rs.getLong(1) + " rows");
            } catch (SQLException e) {
                good = false;
                fail("table " + table, "Cannot be read. [Technical detail: " + e.getMessage() + "]");
            }
        }
        return good;
    }

    /**
     * Phase 3 needs the order status READY. Databases made before Phase 3 do not know it until
     * database/migrations/001_add_ready_order_status.sql has been run, so we look at the column definition.
     */
    private static boolean checkReadyStatus(Connection con) {
        String sql = "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, "orders");
            ps.setString(2, "status");
            try (ResultSet rs = ps.executeQuery()) {
                String type = rs.next() ? rs.getString(1) : "";
                if (type != null && type.toUpperCase().contains("'READY'")) {
                    ok("orders.status accepts READY (Phase 3 order flow)");
                    return true;
                }
                return fail("orders.status (Phase 3)", "The database does not know the order status READY yet. "
                        + "FIX: run database/migrations/001_add_ready_order_status.sql in MySQL Workbench "
                        + "(it keeps all your data and is safe to run again). [Current column type: " + type + "]");
            }
        } catch (SQLException e) {
            return fail("orders.status (Phase 3)", "Could not read the column definition. [Technical detail: "
                    + e.getMessage() + "]");
        }
    }

    /** Phase 4: the bills table needs payment_status, discount_type and discount_value (migration 002). */
    private static boolean checkBillingColumns(Connection con) {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT payment_status, discount_type, discount_value FROM bills LIMIT 0")) {
            ps.executeQuery().close();
            ok("bills has the payment columns (Phase 4 billing)");
            return true;
        } catch (SQLException e) {
            return fail("bills (Phase 4)", "The bills table does not have the payment columns yet. "
                    + "FIX: run database/migrations/002_add_billing_payment_columns.sql in MySQL Workbench "
                    + "(it only adds three columns and keeps all your data; run it once). "
                    + "[Technical detail: " + e.getMessage() + "]");
        }
    }

    /** Phase 6: orders needs history_cleared_at for the Clear Order History button (migration 003). */
    private static boolean checkClearHistoryColumn(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT history_cleared_at FROM orders LIMIT 0")) {
            ps.executeQuery().close();
            ok("orders has history_cleared_at (Phase 6 Clear Order History)");
            return true;
        } catch (SQLException e) {
            return fail("orders (Phase 6)", "The orders table does not have the history_cleared_at column yet, so the "
                    + "Clear Order History button cannot work. FIX: run database/migrations/003_add_history_cleared_flag.sql "
                    + "in MySQL Workbench (it only adds one column and keeps all your data; run it once). "
                    + "[Technical detail: " + e.getMessage() + "]");
        }
    }

    /** Phase 7: the revenue_resets table for the Reset Revenue button (migration 004). */
    private static boolean checkRevenueResets(Connection con) {
        try (PreparedStatement ps = con.prepareStatement("SELECT reset_id, reset_at, last_bill_id FROM revenue_resets LIMIT 0")) {
            ps.executeQuery().close();
            ok("revenue_resets table exists (Reset Revenue)");
            return true;
        } catch (SQLException e) {
            return fail("revenue_resets (Reset Revenue)", "The revenue_resets table does not exist yet, so the Reset Revenue button "
                    + "cannot work. FIX: run database/migrations/004_add_revenue_resets.sql in MySQL Workbench "
                    + "(it only creates one new empty table and keeps all your data; safe to run again). "
                    + "[Technical detail: " + e.getMessage() + "]");
        }
    }

    private static void ok(String text) {
        System.out.println("[ OK ] " + text);
    }

    /** Prints a failed step and returns false so callers can write: return fail(...). */
    private static boolean fail(String step, String message) {
        System.out.println("[FAIL] " + step);
        System.out.println("       " + message);
        return false;
    }
}

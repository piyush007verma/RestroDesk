package com.restaurant.exception;

import java.sql.SQLException;

/**
 * Custom (checked) exception used whenever something goes wrong while talking
 * to the database. Wrapping SQLException in our own exception keeps the
 * Swing screens free from JDBC details.
 * <p>
 * When the cause is "table missing" or "column missing", a FIX sentence is added automatically,
 * so every DAO gives the user the same helpful advice without repeating the code.
 */
public class DatabaseException extends Exception {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(addHint(message, cause), cause);
    }

    private static String addHint(String message, Throwable cause) {
        if (cause instanceof SQLException sql) {
            if ("42S02".equals(sql.getSQLState()) || sql.getErrorCode() == 1146) {
                return message + " FIX: the database exists but its tables are missing. Run database/schema.sql "
                        + "in MySQL Workbench (README, step 2).";
            }
            String text = String.valueOf(sql.getMessage()).toLowerCase();
            if (("42S22".equals(sql.getSQLState()) || sql.getErrorCode() == 1054)
                    && (text.contains("payment_status") || text.contains("discount_type") || text.contains("discount_value"))) {
                return message + " FIX: the bills table needs three new columns. Run "
                        + "database/migrations/002_add_billing_payment_columns.sql in MySQL Workbench "
                        + "(it keeps all your data; run it once).";
            }
            if ("42S22".equals(sql.getSQLState()) || sql.getErrorCode() == 1054) {
                return message + " FIX: a table has different columns than the program expects (an old or half-made table). "
                        + "Start over with an empty database: README, section 'Start over'.";
            }
        }
        if (cause instanceof SQLException sql && (sql.getErrorCode() == 1265
                || String.valueOf(sql.getMessage()).toLowerCase().contains("data truncated"))) {
            return message + " FIX: the database probably does not know the order status READY yet. Run "
                    + "database/migrations/001_add_ready_order_status.sql in MySQL Workbench (it keeps all data).";
        }
        return message;
    }
}

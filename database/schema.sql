-- ============================================================
-- RestoDesk - Restaurant Billing and Table Management System
-- MySQL schema. SAFE TO RUN AGAIN AND AGAIN:
--   * it never drops or deletes anything,
--   * tables are created only if they do not exist yet,
--   * the starter data is added only if it is not there yet.
-- Run this ONE file in MySQL Workbench (File > Open SQL Script > lightning button).
-- Optional demo orders and bills: database/sample_data.sql (run it AFTER this file).
--
-- Every table sets its own character set (utf8mb4, so Hindi and other names work) and a case-insensitive
-- collation (so "Paneer Tikka" and "paneer tikka" count as the same dish), whatever default the database has.
--
-- NOTE: "CREATE TABLE IF NOT EXISTS" does not change a table that already exists.
-- If you changed this file later, add an ALTER TABLE script instead of editing the old tables.
-- ============================================================

CREATE DATABASE IF NOT EXISTS restodesk CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE restodesk;

-- Physical tables in the restaurant (Table Management screen)
CREATE TABLE IF NOT EXISTS restaurant_tables (
    table_id     INT AUTO_INCREMENT PRIMARY KEY,
    table_number INT NOT NULL,
    capacity     INT NOT NULL,
    status       ENUM('AVAILABLE', 'OCCUPIED') NOT NULL DEFAULT 'AVAILABLE',
    UNIQUE KEY uq_tables_number (table_number),
    CONSTRAINT chk_tables_capacity CHECK (capacity > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Food menu (Menu Management screen)
CREATE TABLE IF NOT EXISTS menu_items (
    item_id   INT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100)  NOT NULL,
    category  VARCHAR(50)   NOT NULL,
    price     DECIMAL(10,2) NOT NULL,
    available BOOLEAN       NOT NULL DEFAULT TRUE,
    UNIQUE KEY uq_menu_name (name),
    KEY idx_menu_category (category),
    CONSTRAINT chk_menu_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One order = one sitting of a customer at one table.
-- Assigning a table creates the order with status PENDING (Milestone 2).
-- Kitchen flow (Phase 3): PENDING -> PREPARING -> READY -> SERVED, then PAID (Billing) or CANCELLED.
-- READY is listed LAST on purpose: adding a value at the end of an ENUM is an instant change in MySQL.
-- Databases created before Phase 3 get it from database/migrations/001_add_ready_order_status.sql.
CREATE TABLE IF NOT EXISTS orders (
    order_id       INT AUTO_INCREMENT PRIMARY KEY,
    table_id       INT          NOT NULL,
    customer_name  VARCHAR(100) NOT NULL,
    customer_phone VARCHAR(15)  NULL,
    status         ENUM('PENDING', 'PREPARING', 'SERVED', 'PAID', 'CANCELLED', 'READY') NOT NULL DEFAULT 'PENDING',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Phase 6: NULL = listed in Order History, a time = hidden by "Clear Order History" (nothing is ever deleted)
    history_cleared_at TIMESTAMP NULL DEFAULT NULL,
    KEY idx_orders_table_status (table_id, status),
    KEY idx_orders_created_at (created_at),
    CONSTRAINT fk_orders_table FOREIGN KEY (table_id) REFERENCES restaurant_tables (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- The food items inside an order. unit_price is COPIED from the menu at order time,
-- so changing a menu price later never changes old bills.
CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT AUTO_INCREMENT PRIMARY KEY,
    order_id      INT           NOT NULL,
    item_id       INT           NOT NULL,
    quantity      INT           NOT NULL,
    unit_price    DECIMAL(10,2) NOT NULL,
    UNIQUE KEY uq_order_item (order_id, item_id),
    KEY idx_order_items_item (item_id),
    CONSTRAINT fk_oi_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE,
    CONSTRAINT fk_oi_item  FOREIGN KEY (item_id)  REFERENCES menu_items (item_id),
    CONSTRAINT chk_oi_quantity CHECK (quantity > 0),
    CONSTRAINT chk_oi_price CHECK (unit_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- One bill per order, created at payment time (Billing milestone)
CREATE TABLE IF NOT EXISTS bills (
    bill_id         INT AUTO_INCREMENT PRIMARY KEY,
    order_id        INT           NOT NULL,
    subtotal        DECIMAL(10,2) NOT NULL,
    gst_percent     DECIMAL(5,2)  NOT NULL DEFAULT 5.00,
    gst_amount      DECIMAL(10,2) NOT NULL,
    discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    total_amount    DECIMAL(10,2) NOT NULL,
    payment_method  ENUM('CASH', 'CARD', 'UPI') NOT NULL,
    paid_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Phase 4 (existing databases get these three columns from database/migrations/002_add_billing_payment_columns.sql)
    payment_status  ENUM('PENDING', 'PAID') NOT NULL DEFAULT 'PAID',
    discount_type   ENUM('NONE', 'PERCENT', 'FIXED') NOT NULL DEFAULT 'NONE',
    discount_value  DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    UNIQUE KEY uq_bills_order (order_id),
    KEY idx_bills_paid_at (paid_at),
    CONSTRAINT fk_bills_order FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Relationships:
--   restaurant_tables 1 --- * orders
--   orders            1 --- * order_items * --- 1 menu_items
--   orders            1 --- 1 bills

-- ------------------------------------------------------------
-- Starter data the program needs to be usable (INSERT IGNORE skips rows that already exist,
-- because table_number and menu name are UNIQUE).
-- ------------------------------------------------------------
INSERT IGNORE INTO restaurant_tables (table_number, capacity) VALUES
    (1, 2), (2, 2), (3, 4), (4, 4), (5, 4), (6, 6), (7, 6), (8, 8), (9, 2);

INSERT IGNORE INTO menu_items (name, category, price) VALUES
    ('Paneer Tikka',     'Starters',    260.00),
    ('Veg Spring Rolls', 'Starters',    180.00),
    ('Butter Chicken',   'Main Course', 380.00),
    ('Dal Makhani',      'Main Course', 280.00),
    ('Veg Biryani',      'Main Course', 300.00),
    ('Butter Naan',      'Breads',       60.00),
    ('Gulab Jamun',      'Desserts',    120.00),
    ('Masala Chai',      'Beverages',    50.00),
    ('Fresh Lime Soda',  'Beverages',    90.00);

-- Reset Revenue (Phase 7): one row per press of the button. Bills are never deleted; revenue screens count only bills newer than the latest row.
CREATE TABLE IF NOT EXISTS revenue_resets (
    reset_id       INT AUTO_INCREMENT PRIMARY KEY,
    reset_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_bill_id   INT           NOT NULL DEFAULT 0,
    bills_cleared  INT           NOT NULL DEFAULT 0,
    amount_cleared DECIMAL(12,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

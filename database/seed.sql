-- ============================================================
-- OBSOLETE - DO NOT RUN. Kept only because project files must not be removed.
-- It was written for the old schema.sql and would fail (duplicate keys) on a database made with the current one.
-- Use database/sample_data.sql instead (safe to run again). Phase 3 sample data: see README.
-- ============================================================
-- Sample data so the dashboard has something to show. Run AFTER schema.sql.
USE restodesk;

INSERT INTO restaurant_tables (table_number, capacity, status) VALUES
(1, 2, 'OCCUPIED'), (2, 2, 'AVAILABLE'), (3, 4, 'OCCUPIED'),
(4, 4, 'AVAILABLE'), (5, 4, 'AVAILABLE'), (6, 6, 'OCCUPIED'),
(7, 6, 'AVAILABLE'), (8, 8, 'AVAILABLE'), (9, 2, 'AVAILABLE');

INSERT INTO menu_items (name, category, price) VALUES
('Paneer Tikka',       'Starters',  260.00),
('Veg Spring Rolls',   'Starters',  180.00),
('Butter Chicken',     'Main Course', 380.00),
('Dal Makhani',        'Main Course', 280.00),
('Veg Biryani',        'Main Course', 300.00),
('Butter Naan',        'Breads',     60.00),
('Gulab Jamun',        'Desserts',  120.00),
('Masala Chai',        'Beverages',  50.00),
('Fresh Lime Soda',    'Beverages',  90.00);

INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at) VALUES
(3, 'Aarav Mehta',  '9000000001', 'PREPARING', NOW() - INTERVAL 8 MINUTE),
(6, 'Neha Kapoor',  '9000000002', 'SERVED',    NOW() - INTERVAL 26 MINUTE),
(1, 'Rohan Verma',  '9000000003', 'PENDING',   NOW() - INTERVAL 31 MINUTE),
(5, 'Sara Khan',    '9000000004', 'PAID',      NOW() - INTERVAL 1 HOUR),
(2, 'Vikram Singh', '9000000005', 'PAID',      NOW() - INTERVAL 2 HOUR);

INSERT INTO order_items (order_id, item_id, quantity, unit_price) VALUES
(1, 3, 2, 380.00), (1, 6, 4, 60.00), (1, 8, 2, 50.00),
(2, 1, 2, 260.00), (2, 5, 2, 300.00), (2, 7, 2, 120.00),
(3, 2, 1, 180.00), (3, 4, 1, 280.00), (3, 6, 3, 60.00),
(4, 3, 2, 380.00), (4, 6, 4, 60.00), (4, 9, 2, 90.00),
(5, 4, 2, 280.00), (5, 6, 4, 60.00);

INSERT INTO bills (order_id, subtotal, gst_percent, gst_amount, discount_amount, total_amount, payment_method) VALUES
(4, 1180.00, 5.00, 59.00, 0.00, 1239.00, 'UPI'),
(5,  800.00, 5.00, 40.00, 0.00,  840.00, 'CASH');

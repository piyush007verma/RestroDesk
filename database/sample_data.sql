-- ============================================================
-- RestoDesk - OPTIONAL sample orders and bills, so the Dashboard has something to show.
-- Run AFTER schema.sql. Safe to run again: each sample order is added only once
-- (they are recognised by their phone numbers 9000000001 ... 9000000005).
-- Nothing is deleted or changed except marking the tables of the still-open sample orders as OCCUPIED.
-- Skip this file if you want a completely empty restaurant.
-- ============================================================
USE restodesk;

-- 5 sample orders: three still open (tables 3, 6, 1), two already paid (tables 5, 2)
INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at)
SELECT t.table_id, 'Aarav Mehta', '9000000001', 'PREPARING', NOW() - INTERVAL 8 MINUTE
FROM restaurant_tables t
WHERE t.table_number = 3
  AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_phone = '9000000001');

INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at)
SELECT t.table_id, 'Neha Kapoor', '9000000002', 'SERVED', NOW() - INTERVAL 26 MINUTE
FROM restaurant_tables t
WHERE t.table_number = 6
  AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_phone = '9000000002');

INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at)
SELECT t.table_id, 'Rohan Verma', '9000000003', 'PENDING', NOW() - INTERVAL 31 MINUTE
FROM restaurant_tables t
WHERE t.table_number = 1
  AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_phone = '9000000003');

INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at)
SELECT t.table_id, 'Sara Khan', '9000000004', 'PAID', NOW() - INTERVAL 1 HOUR
FROM restaurant_tables t
WHERE t.table_number = 5
  AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_phone = '9000000004');

INSERT INTO orders (table_id, customer_name, customer_phone, status, created_at)
SELECT t.table_id, 'Vikram Singh', '9000000005', 'PAID', NOW() - INTERVAL 2 HOUR
FROM restaurant_tables t
WHERE t.table_number = 2
  AND NOT EXISTS (SELECT 1 FROM orders WHERE customer_phone = '9000000005');

-- Food on those orders (prices are copied from the menu; UNIQUE(order_id,item_id) + IGNORE makes re-runs harmless)
INSERT IGNORE INTO order_items (order_id, item_id, quantity, unit_price)
SELECT o.order_id, m.item_id, v.qty, m.price
FROM (
    SELECT '9000000001' AS phone, 'Butter Chicken' AS item, 2 AS qty UNION ALL
    SELECT '9000000001', 'Butter Naan',      4 UNION ALL
    SELECT '9000000001', 'Masala Chai',      2 UNION ALL
    SELECT '9000000002', 'Paneer Tikka',     2 UNION ALL
    SELECT '9000000002', 'Veg Biryani',      2 UNION ALL
    SELECT '9000000002', 'Gulab Jamun',      2 UNION ALL
    SELECT '9000000003', 'Veg Spring Rolls', 1 UNION ALL
    SELECT '9000000003', 'Dal Makhani',      1 UNION ALL
    SELECT '9000000003', 'Butter Naan',      3 UNION ALL
    SELECT '9000000004', 'Butter Chicken',   2 UNION ALL
    SELECT '9000000004', 'Butter Naan',      4 UNION ALL
    SELECT '9000000004', 'Fresh Lime Soda',  2 UNION ALL
    SELECT '9000000005', 'Dal Makhani',      2 UNION ALL
    SELECT '9000000005', 'Butter Naan',      4
) AS v
JOIN orders o     ON o.customer_phone = v.phone
JOIN menu_items m ON m.name = v.item;

-- Bills for the two paid orders (subtotal = sum of the order's items, GST 5 percent)
INSERT IGNORE INTO bills (order_id, subtotal, gst_percent, gst_amount, discount_amount, total_amount, payment_method)
SELECT o.order_id, s.subtotal, 5.00, ROUND(s.subtotal * 0.05, 2), 0.00,
       s.subtotal + ROUND(s.subtotal * 0.05, 2), v.method
FROM (
    SELECT '9000000004' AS phone, 'UPI' AS method UNION ALL
    SELECT '9000000005', 'CASH'
) AS v
JOIN orders o ON o.customer_phone = v.phone
JOIN (SELECT order_id, SUM(quantity * unit_price) AS subtotal FROM order_items GROUP BY order_id) AS s
     ON s.order_id = o.order_id;

-- Tables of the sample orders that are still open are occupied
UPDATE restaurant_tables t
JOIN orders o ON o.table_id = t.table_id
SET t.status = 'OCCUPIED'
WHERE o.customer_phone IN ('9000000001', '9000000002', '9000000003')
  AND o.status IN ('PENDING', 'PREPARING', 'SERVED');

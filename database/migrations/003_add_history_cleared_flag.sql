-- ============================================================
-- RestoDesk - Migration 003 (Phase 6: Clear Order History)
--
-- WHY: the "Clear Order History" button must hide old orders from the Order History list WITHOUT deleting anything.
-- Bills, payments, GST, discounts and revenue all belong to those orders, so a real DELETE would be unsafe
-- (bills.order_id and order_items.order_id point to orders). Instead the button writes a time stamp into ONE new
-- column, and Order History simply does not list orders that carry a time stamp.
--
--   orders.history_cleared_at  - NULL = shown in Order History (every existing order starts like this)
--                                a date and time = cleared from the History list at that moment
--
-- SAFE: it only ADDS one column that allows NULL. No table is dropped or recreated, no row is deleted and no existing
-- value is changed. Bills, order items, payments and reports are not touched in any way.
-- RUN IT ONCE. If you run it a second time MySQL answers "Duplicate column name 'history_cleared_at'": that only means
-- it was already applied - nothing is damaged and nothing needs fixing.
-- HOW: MySQL Workbench > File > Open SQL Script > choose this file > click the lightning button.
-- Until it is run, everything keeps working; only the Clear Order History button explains that this step is missing.
-- ============================================================

USE restodesk;

ALTER TABLE orders
    ADD COLUMN history_cleared_at TIMESTAMP NULL DEFAULT NULL;

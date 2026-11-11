-- ============================================================
-- Migration 001 (Phase 3): let orders have the status READY.
--
-- WHY: Phase 3 uses the order flow PENDING -> PREPARING -> READY -> SERVED, but databases created before
--      Phase 3 only know PENDING, PREPARING, SERVED, PAID and CANCELLED.
-- SAFE: it only extends the list of allowed values. No row is read, changed or deleted, nothing is dropped,
--       and READY is added at the END of the list, which MySQL does instantly without rebuilding the table.
--       You can run it again: the second run changes nothing.
-- HOW:  MySQL Workbench > File > Open SQL Script > this file > lightning bolt. Then run check-db.bat.
-- ============================================================
USE restodesk;

ALTER TABLE orders
    MODIFY COLUMN status ENUM('PENDING', 'PREPARING', 'SERVED', 'PAID', 'CANCELLED', 'READY')
    NOT NULL DEFAULT 'PENDING';

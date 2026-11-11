-- ============================================================
-- RestoDesk - Migration 004 (Reset Revenue / Clear Amounts)
--
-- WHY: the "Reset Revenue" button must make the Dashboard and Reports start counting from zero WITHOUT deleting any bill.
--      Each press of the button adds ONE row here: when it happened and the number of the last bill that existed at that moment.
--      Revenue screens then count only bills with a higher bill number than the latest row.
-- SAFE: it only CREATES a new, empty table (IF NOT EXISTS). It does not touch bills, orders, payments, GST or discounts,
--       drops nothing, and can be run again (the second run changes nothing).
-- HOW:  MySQL Workbench > File > Open SQL Script > choose this file > lightning button. Then run check-db.bat.
-- UNDO (only if you ever want the old totals back): delete the rows of this table - the bills were never touched.
-- ============================================================
USE restodesk;

CREATE TABLE IF NOT EXISTS revenue_resets (
    reset_id       INT AUTO_INCREMENT PRIMARY KEY,
    reset_at       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_bill_id   INT           NOT NULL DEFAULT 0,
    bills_cleared  INT           NOT NULL DEFAULT 0,
    amount_cleared DECIMAL(12,2) NOT NULL DEFAULT 0.00
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

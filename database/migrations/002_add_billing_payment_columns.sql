-- ============================================================
-- RestoDesk - Migration 002 (Phase 4: Billing, GST, Discounts and Payment)
--
-- WHY: the existing `bills` table already stores order, subtotal, GST, discount amount, total, payment method and
-- payment time. Three small things were missing to keep a COMPLETE billing history:
--   payment_status  - PAID (a bill row is only written when payment succeeds; PENDING is reserved for later)
--   discount_type   - NONE, PERCENT or FIXED: HOW the discount was given (the receipt must say "10% discount")
--   discount_value  - the number the cashier typed (10 for 10 percent, 50.00 for a fixed Rs. 50)
--
-- SAFE: it only ADDS three columns with defaults. No table is dropped or recreated and no row is changed.
-- Old bills (for example the sample data) become payment_status = 'PAID', discount_type = 'NONE'.
-- RUN IT ONCE. If you run it a second time MySQL answers "Duplicate column name 'payment_status'": that only means it
-- was already applied - nothing is damaged and nothing needs fixing.
-- HOW: MySQL Workbench > File > Open SQL Script > choose this file > click the lightning button.
-- ============================================================

USE restodesk;

ALTER TABLE bills
    ADD COLUMN payment_status ENUM('PENDING', 'PAID') NOT NULL DEFAULT 'PAID',
    ADD COLUMN discount_type  ENUM('NONE', 'PERCENT', 'FIXED') NOT NULL DEFAULT 'NONE',
    ADD COLUMN discount_value DECIMAL(10,2) NOT NULL DEFAULT 0.00;

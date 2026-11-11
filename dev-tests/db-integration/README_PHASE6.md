# Phase 6 tests
`ClearHistoryItTest`: on an EMPTY test database made with schema.sql: drop the column (`ALTER TABLE orders DROP COLUMN history_cleared_at;`, TEST DB ONLY) and run `U`;
then recreate the database with schema.sql and run `A`, then `B` in a NEW JVM.
`P6Test <screenshot folder>` (needs a display or xvfb-run): run after A, after `UPDATE orders SET history_cleared_at = NULL;` (test database only).
Never run these on a database whose data you want to keep.

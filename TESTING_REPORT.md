# RestoDesk v1.0 Final - Testing Report

**Honesty note.** The build sandbox has no real MySQL. All database tests ran against **Dolt `sql-server` (MySQL-compatible, reports 8.0.33)** with the user's own Connector/J jar through the unchanged `DatabaseConnection`, using a separate test settings file (never the user's `db.properties`). UI tests drove the real `MainFrame` under a virtual display (Xvfb). **UNVERIFIED:** real MySQL, Windows `run.bat`, the Windows print dialog, look and feel on other operating systems/fonts, and running migration 003 on the user's own database (not needed again if already run).

## Root cause of the Order History inconsistency
Phase 6 put the "hide cleared orders" condition only in the Order History query. `DashboardDaoImpl.findRecentOrders` (Recent Orders) and `countOrders` (Total Orders card) were separate SQL without it. Fix: `dao/HistoryVisibility` (one shared rule) used by both DAOs; Reports/Billing intentionally do not use it.

## Requested 14-step global clearing test (P7Test, real screens)
| Step | Result |
|---|---|
| 1 create several orders (2 paid, 1 served, 1 pending) | PASS |
| 2 they appear in Dashboard > Recent Orders (and Total Orders card) | PASS |
| 3 they appear in Order History | PASS |
| 4-5 Clear Order History, Cancel: History and Dashboard unchanged | PASS |
| 6 confirm (dialog says 2 finished orders), success message | PASS |
| 7 cleared orders gone from Order History AND Dashboard; Total Orders card = 2 | PASS |
| 8 Orders (open orders only), Billing (served order still billable, both paid bills still listed), Reports (revenue shown) | PASS |
| 9-10 restart (new JVM): still hidden in Dashboard and History; "Show cleared orders" brings them back | PASS |
| 11-12 new order after clearing appears in Dashboard and History | PASS |
| 13-14 revenue / GST / discounts / payment count identical before, after and after restart (1615.95 / 76.95 / 81.0 / 2) | PASS |
Phase A 20 checks, phase B 5 checks, 0 failed. Screenshots: `docs/screenshots/clear_dashboard_before.png`, `clear_dashboard_after.png`.

## New interface tests (P8Test, 14 checks, all PASS)
Alt+number shortcuts, numeric sorting by Price (both directions), selecting a row in a SORTED table loads the same dish, live search filter, "no match" empty-state message, clearing the search, Orders search and empty state, selecting an order after search, sidebar tooltips, loading bar hides after work, History sorted by customer shows the clicked row's details. Quick actions and the clickable Revenue card are covered in P7Test.

## Full regression (fresh empty database for each suite, documented order)
| Suite | Checks | Result |
|---|---|---|
| M2Test (menu/tables, fake data) | 59 | PASS |
| ItTest A / B | 22 / 4 | PASS |
| OrderItTest A / B, P3Test | 40 / 6, 22 | PASS |
| BillItTest U / A / B, P4Test (billing, GST, discounts, payment, rollback) | 20 / 46 / 7, 31 | PASS |
| HistReportItTest A / B, P5Test (history, filters, reports) | 60 / 4, 28 | PASS |
| ClearHistoryItTest U / A / B (no migration, financial fingerprints, restart) | 7 / 31 / 5 | PASS |
| P6Test (Clear button: Cancel, confirm, nothing to clear, show cleared) | 11 | PASS |
| P7Test A / B, P8Test | 20 / 5, 14 | PASS |
| `./build.sh` | Build OK | PASS |
Test adjustments (not app bugs): M2Test ignores the new search box when looking for form fields; P5Test reads card values of any font size >= 20 and only the visible charts (the Dashboard now has a chart too). The first full run of P5Test failed on exactly these two test assumptions, and passes after the test fix.

## Bug review
All SQL uses `PreparedStatement` (the new `HistoryVisibility` appends only constant text and a code-chosen alias); resources use try-with-resources; multi-step writes use transactions; screens use `Background` for database work; sorting uses model/view row conversion everywhere a row index is used (Menu, Orders, Billing, History).

## Not done / limits
No animations by design. Menu/Orders have search boxes; Tables, Billing and Reports use their existing filters. Alt+number shortcuts could clash with a few Linux window managers (untested). Dashboard "Total Orders" excludes cleared orders by design.


---
# Addendum - Reset Revenue (migration 004)
Same honesty note: tested on Dolt (MySQL-compatible), NOT real MySQL; Windows run.bat and printing UNVERIFIED.
P9Test (real screens): **U** (database without the table) 6 checks, **A** 24 checks, **B** (restart) 4 checks - all PASS.
| Requested check | Result |
|---|---|
| Dashboard and Reports show the same revenue before and after the reset | PASS |
| Cancel resets nothing (no row saved, figures unchanged) | PASS |
| Confirm: success message; revenue, payments, average, GST, discounts, daily/monthly tables, best sellers and payment split are zero | PASS |
| Old bills, order items and orders byte-identical after the reset; Billing "Paid bills" still lists all bills | PASS |
| New payment after the reset is counted on both screens (850.50); second reset from the Dashboard works; "Nothing to reset" when nothing is counted | PASS |
| Restart: figures still post-reset, all 5 bills still in the database, 2 resets on record | PASS |
| Without migration 004: Reports and Dashboard work, the reset refuses naming the migration and changes nothing | PASS |
| Full regression (M2, It, Order, P3, Bill, P4, Hist, P5, ClearHistory, P6, P7, P8): same counts as before, 0 failed | PASS |
Note: Reports "Total Orders / Cancelled / Pending" are order counts, not money: Reset Revenue does not change them.

## Fix after user report: Reports > Total Orders after Clear Order History
The user saw Reports still showing the old Total Orders (11) after clearing the history. `ReportsDaoImpl.summary` now applies the shared `HistoryVisibility` rule to the order counts (Total Orders, Cancelled, Pending), so they match Order History and the Dashboard. Money figures are untouched. Re-run: ClearHistoryItTest U/A/B 7/32/5, P6 11, P7 A/B 21/5 (new check: Reports Total Orders = 2 after clearing), P8 14, HistReportItTest 60/4, P5 28, P9 U/A/B 6/24/4 - all PASS.

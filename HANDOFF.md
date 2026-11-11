# HANDOFF - RestoDesk (Restaurant Billing and Table Management System)

> Source of truth for PROGRESS. The source code is the source of truth for IMPLEMENTATION.
> Last updated: v1.0 Final refinement (global history clearing + premium UI), on top of Phase 6 (Clear Order History), Phase 5 (Order History + Sales Reports), Phase 4 (billing), Phase 3 (orders) and Milestones 2 / 2.1 / 2.2.

## 1. Objective and stack

College Java project, team of 4, user is a **beginner** (explain simply, go step by step, wait for feedback between milestones).

Mandatory: menu management, show available/occupied tables, assign tables, take food orders.
Additional: bills, GST and discounts, order status, release table after payment, order history, sales reports, validation and error handling.

Stack: Java 21 LTS, Swing (GUI), MySQL, JDBC (`PreparedStatement`), Git/GitHub. IntelliJ or VS Code.
Forbidden: Spring Boot, Hibernate, React, JS, Python, REST, cloud, external APIs. No Maven/Gradle and no library other than the MySQL Connector/J jar without asking the user first.

**Swing is NOT in the course handout (CHO).** Never claim it is. Business logic, collections, exceptions, JDBC, streams, etc. must stay CHO-aligned.

Product name in the UI: **RestoDesk**. Database name: `restodesk`.

## 2. Architecture (layers)

```
ui (Swing panels) -> service (business rules) -> dao (interface) -> daoimpl (JDBC SQL) -> MySQL
```
Pattern for every feature: **Panel -> Service -> Dao interface -> DaoImpl -> MySQL**.
Rules: SQL only in `*DaoImpl` with `PreparedStatement` + try-with-resources; no business logic in panels; no Swing in service/dao/model; all colours/fonts via `Theme`; every DB call from a screen goes through `Background.run(...)` (SwingWorker); `SQLException` is wrapped in `DatabaseException`; user mistakes are `InvalidInputException` with a ready-to-show message. (Milestone 2 re-checked these rules with grep: no violations.)

Full explanation: `docs/ARCHITECTURE.md` (CHO concept table + Milestone 2 decisions). Team split and Git rules: `docs/TEAM_AND_GIT_RULES.md`.

### Package structure (`src/com/restaurant/`)
| Package | Contents |
|---|---|
| `app` | `Main` - entry point |
| `ui` | `MainFrame` (integration point), `Screen` (enum), `ScreenPanel` (abstract base), `PlaceholderScreen`, `Theme`, `Background` (generic SwingWorker helper), `Messages` (friendly dialogs), `DashboardPanel`, **`MenuPanel`**, **`TablesPanel`**, **`OrdersPanel`**, **`BillingPanel`**, **`ReceiptDialog`**, **`HistoryPanel`**, **`ReportsPanel`** (all real) |
| `ui/components` | `RoundedPanel`, `RoundedButton` (now has a grey disabled look), `StatCard`, `StatusBadge`, `SidebarButton`, `DataTableStyle` (shared look for JTables + pill cell renderer), **`BarChartPanel`** (plain Java 2D bar chart) |
| `service` | `DashboardService`, `MenuService`, `TableService`, **`OrderService`**, **`BillingService`**, **`HistoryService`**, **`ReportsService`** |
| `dao` | `DashboardDao`/`Impl`, `MenuDao`/`Impl`, `TableDao`/`Impl`, **`OrderDao`/`OrderDaoImpl`**, **`BillDao`/`BillDaoImpl`**, **`HistoryDao`/`HistoryDaoImpl`**, **`ReportsDao`/`ReportsDaoImpl`** |
| `model` | `RestaurantTable`, `TableStatus`, `RecentOrder`, `DashboardData`, `MenuItem`, `TableOverview`, **`OrderStatus`, `OrderSummary`, `OrderLine`**, **`BillCalculation`, `Bill`, `PaymentMethod`, `DiscountType`**, **records `HistoryRow`, `HistoryFilter`, `HistoryDetails`, `SalesRow`, `TopItem`, `MethodTotal`, `ReportSummary`, `ReportData`** |
| `db` | `DatabaseConnection` |
| `exception` | `DatabaseException`, `InvalidInputException`, `TableUnavailableException` (all checked) |

Other folders: `database/` (`schema.sql`, `sample_data.sql`, `migrations/`, `seed.sql` = obsolete), `config/db.properties.example`, `lib/`, `docs/`, `dev-tests/` (optional test harness), scripts `build.sh|bat`, `run.sh|bat`.

## 3. Completed

### Milestone 1 - scaffold, theme, sidebar, dashboard (demo-data fallback). See git history / earlier ZIP.

### Milestone 2 - Menu Management and Table Management
**Menu screen** (`MenuPanel`, `MenuService`, `MenuDao`/`MenuDaoImpl`, `MenuItem`): table of all dishes (name, category, price in rupees, Available/Unavailable pill); form to add, edit (click a row), delete (with confirmation) and clear. Category is an editable drop-down (defaults + categories already used). Validation in `MenuService`: name required and max 100 chars; category required and max 50; price must be a number > 0, max 2 decimals, max 99,999,999.99. Duplicate name (case-insensitive) and "dish is used in orders" give friendly messages (see decisions).

**Tables screen** (`TablesPanel`, `TableService`, `TableDao`/`TableDaoImpl`, `TableOverview`): card per table (green Available / red Occupied, seats, customer name), live counts, detail panel with customer name + optional phone, **Assign Table** (free tables only) and **Release Table** (occupied only, with confirmation). List refreshes after every action, including after a failed one. Validation in `TableService`: name 2-100 chars, letters of any language (Hindi works) plus space . ' -; phone optional, 7-14 digits with optional leading +, spaces/hyphens ignored.

**Also done:** `Background`, `Messages`, `DataTableStyle`, `InvalidInputException`, `TableUnavailableException`; `MainFrame.registerScreens()` now wires Menu and Tables with real services (the two Member 1 lines); sidebar footer reads "v0.2 - Milestone 2"; docs updated; `dev-tests/` added.

**Provenance note:** when this session started, the Milestone 2 source files already existed in the project folder (written by an earlier session; HANDOFF.md had not been updated). They were read in full, rebuilt and tested this session; no functional changes were needed.

### Milestone 2.1 - MySQL connection diagnostics (done BEFORE Milestone 3, at the user's request)
**Reported problem:** on the user's computer the Menu screen showed "Database not connected. Check MySQL and config/db.properties (see README)." with 0 items.

**Root cause - what is known and what is not.** The sandbox has no MySQL and cannot see the user's computer, so the exact cause on their machine could NOT be determined by Claude. What the code review found: the old code mapped EVERY failure (missing `config/db.properties`, hidden `.txt` extension, example password left in place, missing Connector/J jar in `lib/`, wrong password, MySQL service not running, database/tables not created, config looked up relative to the IDE's working folder) to the same generic notice and showed the real reason only in a hover tooltip. So the real error was effectively hidden. The scripts and `db.properties.example` keys were checked and are correct. **The user must run `check-db.bat` and report the first `[FAIL]` line; that gives the real root cause. Do not guess.**

**Changes (no schema change, architecture unchanged):**
- `db/DatabaseConnection` rewritten (same `getConnection()` API): finds `config/db.properties` in the current folder or up to two parents (IDE working-folder problem) and reports every path tried; detects `db.properties.txt`; rejects the example password text; checks the driver class `com.mysql.cj.jdbc.Driver` is on the classpath; `explain(SQLException)` turns MySQL errors (1045 password, 1049 unknown database, 1044, connection refused, unknown host, public key retrieval, time zone, bad URL) into "what is wrong + FIX" and ALWAYS appends `[Technical detail: ...]`. Password is read from the file only, never hardcoded.
- NEW `db/DbCheck` (run with `check-db.bat` / `./check-db.sh`): prints `[ OK ]`/`[FAIL]` for Java version, settings file, password changed, driver jar, connection (server name/version, database), and a row count for each of the 5 tables. Never prints the password.
- `ui/Messages.notice(Exception)` now puts the REAL reason on screen (wrapped, 440 px) and prints it to the console; new `Messages.wrap(text, width)`. `DashboardPanel` uses it for its demo-data notice too.
- Scripts: `run.bat/run.sh` now ALWAYS rebuild first (a stale `out/` folder can no longer run old code); `build.bat` now returns a failing exit code on compile errors; new `check-db.bat/.sh`.
- README rewritten with exact MySQL setup steps and a troubleshooting table.
- The app never falls back to in-memory storage for Menu/Tables. Only the Dashboard shows clearly labelled demo numbers when MySQL is down (Milestone 1 behaviour, kept; the user was told and may ask to remove it).

### Phase 3 - Order Management + Menu category filter (this milestone; built ON TOP OF the user's own ZIP)
**Orders screen** (`OrdersPanel`, `OrderService`, `OrderDao`/`OrderDaoImpl`, models `OrderStatus`, `OrderSummary`, `OrderLine`), registered in `MainFrame.registerScreens()`:
- **Active Orders** table: ID, Table, Customer, Qty (number of items), Total, Status pill, Placed (dd/MM HH:mm). Click a row to open the order (customer name, phone and time are shown above the cart).
- **Cart** of the selected order: Item | Category | Price | Qty | Subtotal, live total, buttons `+`, `-`, `Remove`, status buttons (`Send to Kitchen` / `Mark Ready` / `Mark Served` and one step back).
- **Menu card** (right): all dishes with category, price, Available/Unavailable pill, category filter, quantity spinner, `Add to Order` (double-click a dish also adds it). Unavailable dishes cannot be added (button disabled, service refuses, DAO re-checks inside the transaction).
- **Seated table** button = repair path only (an occupied table without an open order). Reuses an open order if one exists, otherwise creates ONE.
- Statuses: PENDING -> PREPARING -> READY -> SERVED, one step at a time, one step back allowed, never skipping. PAID / CANCELLED are NOT set here (billing milestone / release table).
- Rules (all enforced in `OrderService` AND again in `OrderDaoImpl` inside a transaction with `SELECT ... FOR UPDATE`): quantity 1..99; order must still be open; dish must still be available; an EMPTY order cannot be sent to the kitchen; a stale status change (someone else already moved the order) is refused. `unit_price` is copied from the menu when a dish is first added (later menu price changes do not change old orders). The same dish added twice becomes one line (quantity added). Order total is calculated from `order_items` (not stored).
- **Order creation contract kept:** assigning a table on the Tables screen already creates the PENDING order. Orders screen only ADDS FOOD to it; it never creates a second order for a table.

**Menu category fix:** the category column was already present in the Menu table and the Add/Edit form (category is an editable drop-down saved to `menu_items.category`); no bug could be reproduced. Added a **category filter** (drop-down above the Menu table, built from categories in the database) to the Menu screen; Orders screen has the same filter. No second category system was created. Load/save/restart persistence of categories was tested against a database (section 6).

**Schema change (the only one, and it is genuinely required):** `orders.status` ENUM gets the new value `READY`, added at the END of the list (metadata-only change in MySQL; every existing row keeps its value). Safe and re-runnable script: `database/migrations/001_add_ready_order_status.sql` (`ALTER TABLE orders MODIFY COLUMN status ENUM(...,'READY')`). `database/schema.sql` (fresh installs) already contains READY. **Existing databases (like the user's) must run the migration once** - until then everything works except moving an order to READY (the app shows a message pointing to the migration file), and `check-db` prints `[FAIL] orders.status (Phase 3)` with the fix.
Because READY exists, "open order" is now defined as `status NOT IN ('PAID','CANCELLED')` (works before and after the migration). `TableDaoImpl` was changed for this (two SQL conditions) so READY orders keep the table occupied and are cancelled on release.

**Other files touched:** `database/schema.sql` (the user's ZIP still had the OLD destructive version with `DROP DATABASE`; replaced by the safe re-runnable Milestone 2.2 version + READY), `database/seed.sql` (kept, marked OBSOLETE), new `database/sample_data.sql`, `db/DbCheck` (READY check), `exception/DatabaseException` (hint for missing column / READY not stored), `ui/Theme` (READY colour), `ui/MenuPanel` (filter), `service/MenuService` (`usedCategories`), `ui/MainFrame` (OrdersPanel wiring, footer "v0.3 - Phase 3"), `dev-tests/M2Test.java` (combo index 0 -> 1 because the Menu screen now has two drop-downs).
**Protected files, byte-identical to the user's ZIP (verified with cmp):** `config/db.properties`, `config/db.properties.example`, `lib/mysql-connector-j-26.10.0.jar`, `db/DatabaseConnection.java`, `run.bat`, `run.sh`, `build.bat`, `build.sh`, `check-db.bat`, `check-db.sh`.

- FIX: Reports `summary()` order counts (Total Orders, Cancelled, Pending) now use `HistoryVisibility` too (user saw 11 after Clear Order History). Money still ignores the history flag.

### Reset Revenue feature (latest; built ON TOP OF the user's v1.0 ZIP)
- NEW migration `database/migrations/004_add_revenue_resets.sql` (CREATE TABLE IF NOT EXISTS `revenue_resets`: reset_id, reset_at, last_bill_id, bills_cleared, amount_cleared); also in `schema.sql`. Non-destructive; nothing in bills/orders is touched.
- `dao/RevenueBaseline` = ONE shared rule: count only bills with `bill_id >` the latest reset's `last_bill_id` (nothing is excluded before the migration). Used by all money queries in `ReportsDaoImpl` and by `DashboardDaoImpl.totalRevenue`. Order counts (Total/Cancelled/Pending) and Billing/History are NOT affected.
- `ReportsDao/Impl/Service`: `latestReset()`, `previewReset()`, `resetRevenue()` (one INSERT in a transaction). `model/RevenueReset` record.
- UI: `ui/RevenueResetAction` (shared flow: preview -> confirm dialog, Cancel default -> reset -> success message); red "Reset Revenue" button + "counted from the reset on ..." note in `ReportsPanel`; button + short note in `DashboardPanel` quick actions (only when a ReportsService is given); `DbCheck` new [ OK ]/[FAIL] line.
- Tests: `dev-tests/P9Test.java` (U/A/B). Screenshots: docs/screenshots/reset_revenue_*.png.
- Protected files unchanged (hash-compared with the user's ZIP).

### v1.0 Final refinement (this milestone; built ON TOP OF the user's Phase 6 ZIP; NO new migration)
- ROOT CAUSE of "cleared orders still on the Dashboard": the hiding condition existed only in `HistoryDaoImpl`; `DashboardDaoImpl` (Recent Orders + Total Orders) had no condition. FIX: new `dao/HistoryVisibility` (`columnExists`, `visibleOnly(con, alias)`), used by `HistoryDaoImpl` and `DashboardDaoImpl`. Reports, Billing and revenue deliberately do not use it. Orders/Tables show only open orders (never cleared).
- UI: `SidebarButton` icons (Java2D), hover/active/focus ring; `RoundedButton` pressed state + focus ring; `StatCard.setOnClick` + auto-shrinking value font; `DashboardPanel` (3-arg constructor with navigator and ReportsService, quick actions, 7-day chart, double-click row); `SearchField` (Menu, Orders); `DataTableStyle` (`enableSorting`, `filterRows`, `emptyState`, `selectedModelRow`, `selectModelRow`); `Background` busy counter + `MainFrame` loading bar and Alt+1..7 shortcuts; `Main` sets dialog/tooltip fonts; History/Orders column widths; sidebar text "v1.0 - Final".
- IMPORTANT for future code: sorted tables -> NEVER use `getSelectedRow()` / `setRowSelectionInterval()` on Menu, Orders (active list), Billing list, History list; use `DataTableStyle.selectedModelRow/selectModelRow`.
- Tests: `P7Test` (14-step global clearing test, phases A/B), `P8Test` (sorting/search/shortcuts); older tests adapted (M2Test ignores SearchField, P5Test font >= 20 and visible charts only).
- Docs: README section 7b, VIVA_PREPARATION (50 questions, sections 16/16b), TESTING_REPORT.md, docs/screenshots/*.png.
- Protected files unchanged (hash-compared): db.properties, db.example, jar, DatabaseConnection.java, run/build/check-db scripts.

### Phase 6 - Final integration, Clear Order History, UI polish, docs (this milestone; built ON TOP OF the user's Phase 5 ZIP)
- NEW migration `database/migrations/003_add_history_cleared_flag.sql`: `ALTER TABLE orders ADD COLUMN history_cleared_at TIMESTAMP NULL DEFAULT NULL;` (non-destructive; second run = harmless "Duplicate column"). `schema.sql` includes the column for fresh databases.
- Clear Order History is LOGICAL (no DELETE): sets `history_cleared_at = NOW()` on PAID/CANCELLED orders in one transaction. History hides flagged rows unless "Show cleared orders" is ticked (cleared IDs show `#id*`). Bills, order_items, Reports, Dashboard, Billing never read the flag.
- Code: `HistoryFilter` (+`includeCleared`, 7-arg constructor kept), `HistoryRow` (+`cleared`), `HistoryDao/Impl` (`isClearingAvailable`, `countClearableOrders`, `clearClosedOrders`), `HistoryService` (`isClearingAvailable`, `countClearableOrders`, `clearHistory`), `HistoryPanel` (red button, Cancel-default confirmation dialog, success/empty/migration-missing messages, wider columns), `DbCheck` (new [ OK ]/[FAIL] line for the column), `MainFrame` (sidebar "v0.6 - Phase 6").
- Before migration 003 the app works as before; the button explains the migration.
- New docs: README.md (overview, Phase 6 checklist, limitations), VIVA_PREPARATION.md (17 sections, 50 Q&A), TESTING_REPORT.md, docs/ARCHITECTURE.md Phase 6 section.
- New tests: `dev-tests/db-integration/ClearHistoryItTest.java` (U/A/B), `dev-tests/P6Test.java` (real UI: Cancel then Confirm).
- PROTECTED FILES UNCHANGED (SHA-256 compared with the start of Phase 6): `config/db.properties`, `config/db.example`, `lib/mysql-connector-j-26.10.0.jar`, `DatabaseConnection.java`, `run.bat/.sh`, `build.bat/.sh`, `check-db.bat/.sh`. The ZIP omits `config/db.properties` (privacy rule).

### Phase 5 - Order History + Sales Reports (previous milestone; built ON TOP OF the user's Phase 4 ZIP; NO database change)
Both screens are READ-ONLY (no insert/update/delete anywhere in `HistoryDao` / `ReportsDao`) and use only existing tables: `orders` LEFT JOIN `bills`, `order_items`, `restaurant_tables`, `menu_items`. There is NO separate `payments` table in this schema: payment method / status / time live in `bills` (`payment_method`, `payment_status`, `paid_at`). So **no migration was needed** (the latest migration is still 002).
**Order History** (`HistoryPanel`, `HistoryService`, `HistoryDao`/`HistoryDaoImpl`), wired in `MainFrame.registerScreens()`:
- List of orders of EVERY status, newest first (max 500 rows, the screen says when it is cut): ID, Table, Customer, Date, Status pill, Total (bill total, "-" if never billed), Payment (PAID / UNPAID / N/A for cancelled), Method.
- Filters (all combine): Order ID, Table number, Order status, Payment status (Paid / Unpaid), Payment method, date range (check box "Filter by date" + two date spinners; created date of the order), `Search` button, `Reset`. Changing a combo box or a date searches by itself; Enter in a text box searches. Bad input ("abc", 0, start date after end date) shows a message and keeps the old list (`HistoryService.buildFilter`).
- Details card of the selected order: items (name, qty, unit price, subtotal), bill breakdown (subtotal, discount with % or "fixed", taxable, GST with %, grand total), payment method, paid time, order + payment badges, `View Receipt` (same receipt text as Billing: `BillingService.buildReceipt`; the button is disabled when the order has no bill). Unbilled / cancelled orders show their items and a note.
- Payment status rule: PAID = bill row with `payment_status='PAID'`; CANCELLED order without paid bill = N/A; everything else = UNPAID. The Unpaid filter excludes cancelled orders.
- Releasing a table or creating new orders never changes old rows (history reads orders/bills/items only).
**Reports** (`ReportsPanel`, `ReportsService`, `ReportsDao`/`ReportsDaoImpl`, `BarChartPanel`):
- Period drop-down: Today, Last 7 days (today + 6 days before), This month, All time (from 2000-01-01), Custom range (two date spinners + Apply; start after end or too old = message). `ReportsService.resolve(period, from, to, today)` holds the date rules.
- Cards: Total Orders (all statuses created in the period), Total Revenue, Payments Done (completed payments), Pending Payments (= orders with status SERVED created in the period, i.e. waiting for payment), Avg Order Value (SQL `AVG` of paid bills, 2 decimals). Footnote: cancelled orders, discounts given, GST collected.
- **Money = bills with `payment_status='PAID'` only, by `paid_at` date.** One bill per order (UNIQUE `uq_bills_order`), so nothing is counted twice; unpaid/pending bills and cancelled orders add nothing.
- Four sections, each a bar chart + table: Daily sales (date, orders, sales, discount, GST), Monthly sales (month, orders, revenue, discount, GST), Best-selling items (name, qty, sales = qty x unit_price copied at order time; only PAID orders; top 8; name from `menu_items`), Payment methods (Cash / UPI / Card: payments + amount; all three always listed, zero when unused).
- All sums are SQL (`SUM`, `COUNT`, `AVG`, `GROUP BY`, `ORDER BY`, `LIMIT`) with `PreparedStatement`; dates compared as `>= from 00:00` and `< day after to`.
- The Reports screen reloads each time it is opened and runs through `Background.run`.
Files created: `model/HistoryRow, HistoryFilter, HistoryDetails, SalesRow, TopItem, MethodTotal, ReportSummary, ReportData` (Java records), `dao/HistoryDao, HistoryDaoImpl, ReportsDao, ReportsDaoImpl`, `service/HistoryService, ReportsService`, `ui/components/BarChartPanel`, `dev-tests/P5Test.java`, `dev-tests/db-integration/HistReportItTest.java`.
Files modified: `ui/HistoryPanel`, `ui/ReportsPanel` (placeholders -> real), `ui/MainFrame` (3 wiring changes, footer "v0.5 - Phase 5"), `README.md`, `HANDOFF.md`, `NEXT_CLAUDE_PROMPT.txt`, `docs/ARCHITECTURE.md`, dev-test READMEs.
**Protected files, byte-identical to the user's Phase 4 ZIP (verified with cmp):** `config/db.properties.example`, `lib/mysql-connector-j-26.10.0.jar`, `db/DatabaseConnection.java`, `run.bat`, `run.sh`, `build.bat`, `build.sh`, `check-db.bat`, `check-db.sh`. The user's ZIP contained their real `config/db.properties` (never read or printed); it is NOT in the delivered ZIP. The user's ZIP also had a `Paid Bills` folder with a bill PDF they printed (user data, not project code; not copied into the delivered ZIP).
**User-confirmed on their real MySQL 8.0.46 (from screenshots, not from sandbox tests):** `check-db.bat` all `[ OK ]` (incl. READY and bills payment columns); they ran a full order -> bill -> UPI payment with a 20% discount; the printed receipt (saved by them as a PDF; they had typed `.bill` as the extension) showed 4 x Butter Naan 240.00 - 20% = 192.00 + GST 9.60 = 201.60, PAID.

### Phase 4 - Billing, GST, Discounts, Payment, automatic table release (previous milestone; built ON TOP OF the user's Phase 3 ZIP)
**Billing screen** (`BillingPanel`, `ReceiptDialog`, `BillingService`, `BillDao`/`BillDaoImpl`, models `BillCalculation`, `Bill`, `PaymentMethod`, `DiscountType`), registered in `MainFrame.registerScreens()` (footer "v0.4 - Phase 4"):
- **List card** with a drop-down: `Ready to bill` = ONLY orders with status SERVED (order id, table, customer, quantity, total, status). Orders that are PENDING / PREPARING / READY / PAID / CANCELLED are never listed. `Paid bills` = the 50 newest saved bills (bill, table, customer, paid time, total, method), read-only; survives restarts.
- **Items card:** food items of the selected order (item, qty, unit price, subtotal).
- **Bill card:** discount type (No discount / Percentage % / Fixed amount), discount value, GST % field (starts at `BillingService.DEFAULT_GST_PERCENT` = 5.00, the ONLY place the default is written; any value 0..100 can be typed), live breakdown Subtotal / Discount / Taxable amount / GST / Grand Total (Indian rupee sign), payment method (Cash / UPI / Card), `Confirm Payment`. Invalid input shows a clear message under the form and disables payment (nothing reaches the database).
- **Payment flow:** Confirm Payment -> question "Take payment of X by UPI for Order #n (Table t)?" -> No = nothing happens (table stays OCCUPIED) -> Yes = button shows "Processing...", ONE database transaction -> "Payment successful" message with the bill number -> receipt window (Print button uses Java's built-in `JTextArea.print()`, no PDF library; on Windows pick "Microsoft Print to PDF" to get a PDF). `View Receipt` re-opens it for any paid bill.
- **Formula** (`BillCalculation`, pure Java, `BigDecimal`, 2 decimals, `RoundingMode.HALF_UP`): discount = percent of subtotal or fixed amount; taxable = subtotal - discount; GST = taxable x gst% / 100; grand total = taxable + GST. Refused with a message: negative discount, percentage > 100, fixed discount > subtotal, GST outside 0..100, empty bill. 100% (or fixed = subtotal) discount gives a total of 0.00 (never negative). Typed text accepted: digits with at most 2 decimals (`10`, `12.50`); `abc`, `-5`, `10.999`, `1e3` are refused.
- **Payment transaction** (`BillDaoImpl.payOrder`, `setAutoCommit(false)`, all PreparedStatement + try-with-resources): (1) `SELECT ... FOR UPDATE` on the order: must exist, be SERVED (PAID -> "already paid", CANCELLED / not served -> refused); (2) no bill row yet; (3) re-read the items and re-calculate: must equal the bill the cashier confirmed, otherwise "items changed ... nothing was paid"; (4) INSERT bill (payment_status PAID); (5) `UPDATE orders SET status='PAID' ... AND status='SERVED'` (the existing final status is PAID - no new order status was needed); (6) `UPDATE restaurant_tables SET status='AVAILABLE'` but ONLY if no OTHER open order uses that table; (7) commit. Any failure -> rollback: no bill, order still SERVED, table still OCCUPIED. Duplicate payment is stopped three ways: row lock + status check, bill-exists check, `UNIQUE(order_id)` on bills.
- **Business rules kept:** generating/showing a bill, choosing a method and cancelling the question NEVER touch the database. The ONLY place a table is released by billing is step 6 of `payOrder`. `TableDao.releaseTable` (Tables screen "Release Table", cancels the order) is unchanged and is NOT used by billing.
- Revenue on the Dashboard comes from `bills.total_amount`, so it now grows with real payments (existing `DashboardDaoImpl`, unchanged).

**Schema change (the only one; genuinely required):** `bills` gets 3 columns with defaults: `payment_status ENUM('PENDING','PAID') DEFAULT 'PAID'`, `discount_type ENUM('NONE','PERCENT','FIXED') DEFAULT 'NONE'`, `discount_value DECIMAL(10,2) DEFAULT 0`. Why: the old table stored only the discount AMOUNT, so a receipt could not say "10% discount", and had no payment status. Everything else the spec asked for (bill id, order id, subtotal, discount, GST, grand total, method, paid time) already existed and was reused. File: `database/migrations/002_add_billing_payment_columns.sql` (also copied to the deliverables). **Run it ONCE** in MySQL Workbench; a second run only says "Duplicate column name" (harmless). Existing bills (sample data) become PAID / NONE / 0. `database/schema.sql` (fresh installs) already contains the columns. `check-db` prints `[FAIL] bills (Phase 4)` with the fix until the migration is run; a DAO error about those columns shows a hint pointing to the migration (the old "start over" hint is no longer shown for them).
Files created: `model/BillCalculation, Bill, PaymentMethod, DiscountType`, `dao/BillDao, BillDaoImpl`, `service/BillingService`, `ui/ReceiptDialog`, `database/migrations/002_...sql`, `dev-tests/P4Test.java`, `dev-tests/db-integration/BillItTest.java`.
Files modified: `ui/BillingPanel` (placeholder -> real), `ui/MainFrame` (wiring + footer), `db/DbCheck` (new check), `exception/DatabaseException` (hint), `database/schema.sql` (3 columns), README, HANDOFF, NEXT_CLAUDE_PROMPT, `docs/ARCHITECTURE.md`, dev-test READMEs.
**Protected files, byte-identical to the user's Phase 3 ZIP (verified with cmp):** `config/db.properties.example`, `lib/mysql-connector-j-26.10.0.jar`, `db/DatabaseConnection.java`, `run.bat`, `run.sh`, `build.bat`, `build.sh`, `check-db.bat`, `check-db.sh`. **`config/db.properties` is intentionally NOT in the delivered ZIP** (the user asked for no private passwords in the ZIP): they keep their own file; if they unzip into a NEW folder they must copy their `db.properties` into its `config` folder.

## 4. DATABASE DECISIONS (Milestone 2; Phase 3 added READY, Phase 4 added 3 bills columns)

- Assign table = in ONE transaction: `UPDATE restaurant_tables SET status='OCCUPIED' WHERE table_id=? AND status='AVAILABLE'` (0 rows changed -> `TableUnavailableException`, rollback) then `INSERT INTO orders (table_id, customer_name, customer_phone, status) ... 'PENDING'`. Returns the new `order_id`.
- Release table = in ONE transaction: `UPDATE ... SET status='AVAILABLE' WHERE ... AND status='OCCUPIED'` (0 rows -> `TableUnavailableException`) then `UPDATE orders SET status='CANCELLED' WHERE table_id=? AND status NOT IN ('PAID','CANCELLED')` (Phase 3: was an explicit list without READY).
  - **Flag for the user:** this cancels open orders even if food was already ordered on them (the confirm dialog says so: "use this when guests leave without paying"). A stricter rule (refuse when the order has items) is easy to add if the user wants it. Billing (Milestone 4) must free the table itself after payment and mark the order PAID, NOT call release.
- "Open order" of a table = latest order (max `order_id`) with status NOT IN ('PAID','CANCELLED') (PENDING, PREPARING, READY, SERVED) (`TableDaoImpl.findAllWithCustomers`, exposed as `TableOverview.getOpenOrderId()`, 0 if none).
- **Contract for Milestone 3 (Order Management):** a seated table already has its PENDING order. Add `order_items` to THAT order. Never create a second order for a seated table. Copy `menu_items.price` into `order_items.unit_price`. Only offer menu items with `available = TRUE`.
- Menu delete: if `order_items` references the dish the FK blocks it (SQLState 23xxx) -> friendly message "untick Available instead". Menu names are unique (case-insensitive collation).

### Schema (`database/schema.sql`, DB `restodesk`; safe to re-run: `CREATE ... IF NOT EXISTS`, `INSERT IGNORE`, never drops anything)
| Table | Columns |
|---|---|
| `restaurant_tables` | `table_id` PK, `table_number` UNIQUE, `capacity` >0, `status` ENUM(AVAILABLE,OCCUPIED) |
| `menu_items` | `item_id` PK, `name` UNIQUE, `category`, `price` >=0, `available` BOOLEAN |
| `orders` | `order_id` PK, `table_id` FK, `customer_name`, `customer_phone`, `status` ENUM(PENDING,PREPARING,SERVED,PAID,CANCELLED,READY) (READY added in Phase 3, migration 001), `created_at` |
| `order_items` | `order_item_id` PK, `order_id` FK (ON DELETE CASCADE), `item_id` FK, `quantity` >0, `unit_price`, UNIQUE(order_id,item_id) |
| `bills` | `bill_id` PK, `order_id` FK UNIQUE, `subtotal`, `gst_percent` (default 5.00), `gst_amount`, `discount_amount`, `total_amount`, `payment_method` ENUM(CASH,CARD,UPI), `paid_at`, **`payment_status`** ENUM(PENDING,PAID) default PAID, **`discount_type`** ENUM(NONE,PERCENT,FIXED), **`discount_value`** (Phase 4, migration 002) |

Relationships: restaurant_tables 1-* orders; orders 1-* order_items *-1 menu_items; orders 1-1 bills. Order total = sum(quantity * unit_price) from `order_items` (not stored).

## 5. Changed files in Milestone 2 (vs Milestone 1)

New: `model/MenuItem`, `model/TableOverview`, `dao/MenuDao`, `dao/MenuDaoImpl`, `dao/TableDao`, `dao/TableDaoImpl`, `service/MenuService`, `service/TableService`, `exception/InvalidInputException`, `exception/TableUnavailableException`, `ui/Background`, `ui/Messages`, `ui/components/DataTableStyle`, `dev-tests/*`.
Milestone 2.1 also: new `db/DbCheck`, `check-db.bat`, `check-db.sh`; modified `db/DatabaseConnection`, `ui/Messages`, `ui/DashboardPanel` (one line), `run.bat`, `run.sh`, `build.bat`, `README.md`.
Milestone 2 modified: `ui/MenuPanel` and `ui/TablesPanel` (placeholders -> real), `ui/MainFrame` (registerScreens lines + footer text), `ui/components/RoundedButton` (disabled look), `docs/ARCHITECTURE.md`, `docs/TEAM_AND_GIT_RULES.md`, `.gitignore`, `HANDOFF.md`, `NEXT_CLAUDE_PROMPT.txt`.
Unchanged: `Dashboard*`, `DatabaseConnection`, `Theme`, `schema.sql`, `seed.sql`.
Note: `Theme`/`components` are owned by Member 4 - `RoundedButton` and `DataTableStyle` were touched/added by Milestone 2; tell the team.

## 6. Testing status - READ THIS

### Phase 6 tests (this session) - see TESTING_REPORT.md
Same Dolt (NOT real MySQL) setup. ClearHistoryItTest U 7 / A 31 / B 5, P6Test 11, and all older tests re-run on fresh databases: M2Test 59, ItTest 22+4, OrderItTest 40+6, P3Test 22, BillItTest 20+46+7, P4Test 31, HistReportItTest 60+4, P5Test 28 - all passed. UNVERIFIED: real MySQL, Windows run.bat, print dialog.

### Phase 5 tests (previous session)
**IMPORTANT - what the database was:** no real MySQL in the sandbox. Everything that touches a database ran against **Dolt `sql-server` 2.7.0 (reports MySQL 8.0.33) with the user's own Connector/J jar 26.10.0** through the unchanged `DatabaseConnection` (a separate test `db.properties`, never the user's file). Dolt is MySQL-compatible, NOT real MySQL (`DATE_FORMAT`, `DATE`, `GROUP BY`, `LEFT JOIN`, `COALESCE`, `AVG` worked there; they are standard MySQL).

Actually performed:
- `./build.sh` -> Build OK; ZIP also rebuilt in a fresh folder.
- `dev-tests/db-integration/HistReportItTest.java` phase A: **60 passed, 0 failed**; phase B (new JVM = restart): **4 passed**. Builds 6 orders (3 paid by UPI / Cash / Card with 10% / fixed 100 / no discount, 1 open, 1 served-unpaid, 1 cancelled), moves two of them to 2 and 40 days ago with plain SQL, then checks every history filter and every report number against hand-calculated values (revenue 2361.45, average 787.15, discounts 181.00, GST 112.45, daily/monthly rows, best-selling 6 x Butter Chicken, payment methods) AND against separate raw SQL. Also: a PENDING bill row is not counted as revenue, a second bill for an order is refused by the database, input validation, date-range rules, the history screen changes no rows, released tables do not change history.
- `dev-tests/P5Test.java` (real `MainFrame`, real DAOs): **28 passed, 0 failed**: History opens and lists 7 orders; search by Order ID and Table; status / payment / method / date filters; bad input message; Reset; details, receipt button enabled/disabled; no Delete/Edit button on History; Reports cards (7 orders, 2,361.45, 3 payments, 787.15), tables, 4 charts, Today and custom-range messages; other screens still open. Screenshots reviewed (a few columns are tight; text is clipped with ".." only in the narrowest cells).
- Regression on a fresh database each: `OrderItTest` A **40** + B **6**; `P3Test` **22**; `BillItTest` U **20** / A **46** / B **7**; `P4Test` **31** (needs a database with NO bills yet); `M2Test` **59 / 59** (run from the project folder); `DbCheck` all `[ OK ]`.
- Protected files compared byte-for-byte with the user's Phase 4 ZIP: identical.
One real bug found by the tests and fixed: changing the History date spinners did not trigger a search.

NOT tested (needs the user's computer): `run.bat` on Windows (unchanged script), the new screens on the user's real MySQL (queries are standard; first thing to check), real MySQL `ONLY_FULL_GROUP_BY` (the grouped queries select only grouped expressions and aggregates), Windows fonts / the real print dialog, very large data (History is capped at 500 rows; reports are SQL aggregates).

### Phase 4 tests (previous session)
**IMPORTANT - what the database was:** no real MySQL in the sandbox. Everything that touches a database ran against **Dolt `sql-server` 2.7.0 (reports MySQL 8.0.33) with the user's own Connector/J jar 26.10.0** through the unchanged `DatabaseConnection`. Dolt is MySQL-compatible, NOT real MySQL. The tests used a separate test `db.properties` (Dolt port), never the user's file.

Actually performed:
- `./build.sh` -> Build OK; also built again from the final ZIP in a fresh folder.
- Migration 002 on a database built from the user's Phase 3 schema + sample data: the 2 old bills kept every old value, got PAID / NONE / 0.00; second run only gives "column already exists".
- `BillItTest U` (money formulas, no DB): **20 passed**. `BillItTest A` (billing + payment on the database): **46 passed**. `BillItTest B` (new JVM = restart): **7 passed**. Covers: only SERVED orders listed; PENDING/PREPARING/READY not billable (service and database); items fetched; percent and fixed discount; GST; rounding; BigDecimal 0.10+0.20; negative / >100 / above-subtotal / bad text refused; Cash, UPI, Card payments saved with the right values; table OCCUPIED before payment (generating the bill releases nothing) and AVAILABLE after; order becomes PAID and leaves Active Orders; duplicate payment refused (service-level and database-level) and nothing recorded; order items changed after the bill was shown -> payment refused; **simulated failure in the last step (table update) -> full rollback: no bill, order still SERVED, table still OCCUPIED**, then the same bill pays normally; table with ANOTHER open order stays OCCUPIED; receipt text; bills/history after restart.
- `P4Test` (real `MainFrame`, real Billing screen, real DAOs): **31 passed**: list shows only SERVED orders, items, live totals, discount/GST editing, invalid values disable payment with a message, **No on the confirmation = table stays OCCUPIED**, Yes = success message + receipt window + table AVAILABLE + order leaves list, Paid bills mode, View Receipt, read-only paid bill, a second payment by Cash, other screens still open. Screenshots (billing, paid bills, receipt, dashboard) reviewed; Dashboard revenue = sum of the two bills.
- Regression: `M2Test` **59 / 59**, `OrderItTest` A **40** + B **6**, `P3Test` **22 / 22**, `DbCheck` all `[ OK ]` (incl. bills payment columns).
- Protected files compared byte-for-byte with the user's Phase 3 ZIP: identical.

NOT tested (needs the user's computer): `run.bat` on Windows (unchanged script), the user's real MySQL and running migration 002 in Workbench, the real print dialog / "Microsoft Print to PDF" (printing code is Java's `JTextArea.print()`; only the receipt text and window were verified), look and fonts on Windows, two computers paying the same order at the same instant (SQL uses row locks; only simulated by the stale-screen tests). Note: a harness quirk, not an app bug: `M2Test` must be run from the project folder (it hangs if the current folder's `config/db.properties` points to a live database); P3Test/P4Test want the screenshot folder to exist.

### Phase 3 tests (kept for history)
**IMPORTANT - what the database was:** the sandbox has no MySQL. Everything below that touches a database ran against **Dolt `sql-server` 2.7.0 (reports itself as MySQL 8.0.33), using the user's own Connector/J jar 26.10.0**, through the unchanged `DatabaseConnection`. Dolt is MySQL-compatible, NOT real MySQL. The user must repeat the checklist on real MySQL (section 10). The user's real `config/db.properties` was never used by the tests (a separate test copy pointing to the Dolt port was).

Actually performed:
- `./build.sh` -> Build OK.
- `dev-tests/M2Test.java` (fake DAOs, real Menu/Tables screens): **59 passed, 0 failed** (regression).
- `dev-tests/db-integration/OrderItTest.java` phase A: **40 passed, 0 failed**; phase B in a NEW JVM (= application restarted): **6 passed, 0 failed**. Covers: assigning a table creates exactly one PENDING order; items added; same dish merged; subtotals/totals; `+`/`-`; cannot drop below 1; zero/negative/over-99 refused; dish made unavailable meanwhile refused; menu price change does not change the order line; remove; empty order cannot go to kitchen; PENDING->PREPARING->READY->SERVED, no skipping, one step back, stale status change refused; READY order keeps table occupied; reuse of an open order (no duplicate); closed order refuses changes; releasing a table cancels the order; category save/edit/reload; everything persisted after restart.
- `dev-tests/P3Test.java`: **22 passed, 0 failed** - the REAL `MainFrame` with real DAOs: Orders screen opens from the sidebar, orders and menu load, select order, add dish with quantity 2, cart columns, total label, `+`, `-`, category filter, Send to Kitchen, Mark Ready, Remove, Menu / Tables / Dashboard still open. Screenshots reviewed.
- `DbCheck` against that database: all `[ OK ]` including `orders.status accepts READY`.
- Migration 001 verified earlier on a database built from the user's OLD schema + seed: row counts and checksums unchanged, second run harmless; before the migration the old statuses, Tables, Dashboard and order list worked.
- Protected files compared byte-for-byte with the user's ZIP: identical.

NOT tested (needs the user's computer): `run.bat` on Windows (never executed; the script is unchanged from the user's working ZIP), the user's real MySQL server and Workbench (running migration 001 there), Connector/J error wording from a real MySQL 1265 error (the app also recognises other "enum / truncated" wordings), two computers using the same table at the same moment (the SQL uses row locks; only simulated by a stale-status test), look and feel on Windows fonts.

### Earlier testing (Milestones 2 / 2.1, kept for history)
**Actually tested (Milestone 2 session):**
- Full rebuild with `./build.sh` -> `Build OK`.
- `dev-tests/M2Test.java`: **59 checks, all passed**, using the REAL panels and services with in-memory FAKE DAOs: menu load, every validation message, add, duplicate-name message, select/edit, availability toggle, delete with confirmation, table load and counts, assign validation, assign creates one PENDING order and occupies the table, phone cleaning, duplicate assignment refused and list refreshed, release with confirmation, release cancels the order, table can be re-assigned, service rules (Hindi name, limits, price 1E+3 etc.).
- Real app with NO database: all 7 screens open without exceptions; Dashboard shows demo data; Menu and Tables show the orange "Database not connected" notice; screenshots reviewed visually.
- Architecture rules checked with grep (SQL only in DaoImpl, no Swing in service/dao/model).

**NOT tested - no MySQL server or Connector/J jar could be installed in the build sandbox (apt and Maven Central blocked):**
- `MenuDaoImpl` and `TableDaoImpl` SQL against a real MySQL (including the transactions, `findAllWithCustomers` correlated subquery, generated keys, constraint-violation handling).
- Real duplicate-assignment race between two computers.
- `schema.sql` / `seed.sql` execution, live dashboard.
These were only reviewed by reading.

**Milestone 2.1 testing (sandbox, no MySQL):** `DbCheck` was run and produced the correct, specific `[FAIL]` message for: no settings file, hidden `.txt` file, example password, missing driver jar, and it also found the file when started from a sub-folder. `explain()` was tested with 9 synthetic MySQL errors (all pass). `DbCheck`'s success path and the unknown-database / wrong-password / missing-table failures were exercised with a FAKE driver (not MySQL) to prove the checker's own logic. The notice rendering was checked in screenshots; the 59 Milestone 2 checks still pass. NOT verified: a real connection, real CRUD persistence, Windows `.bat` behaviour (written carefully, never executed), and the Connector/J error texts of real servers (matched by SQL error code / state where possible).

**First job when MySQL is available:** run `schema.sql`, `seed.sql`, add the jar, create `config/db.properties`, then in the app: add/edit/delete a dish (also try a duplicate name and deleting a dish from the seed that is used in orders, e.g. Butter Chicken), assign a free table, try assigning it again from a second running copy of the app, release it, and check the Dashboard recent orders. Fix anything that fails and record it here.

## 7. Pending

| Milestone | Owner | Scope |
|---|---|---|
| 3 | Member 2 | DONE (Phase 3). Orders screen, cart, statuses, READY migration, Menu category filter |
| 4 | Member 3 | DONE (Phase 4). Original scope was: Billing: `Bill`, `BillingService` (GST, discount), `BillDao+Impl`, payment, ONE transaction (insert bill + order PAID + table AVAILABLE), bill export to file |
| 5 | Member 4 | DONE (Phase 5). Order History + Reports screens (read-only, no migration). CSV/txt export of reports was NOT done (optional, not requested) |
| 6 | All | DONE (Phase 6). Clear Order History, polish, integration tests, README, VIVA_PREPARATION, TESTING_REPORT |

## 8. Important classes and methods

- `MainFrame.registerScreens()` - integration point, one line per screen. `navigateTo(Screen)` calls `onShow()` of the screen.
- `Background.run(Callable<T> task, Consumer<T> onSuccess, Consumer<Exception> onError)` - run DB work off the UI thread; callbacks run on the UI thread.
- `Messages.error(parent, Exception)`, `Messages.confirm(...)`, `Messages.notice(Exception)` - friendly dialogs / orange notice text.
- `MenuService`: `getAllItems()`, `addItem(name, category, priceText, available)`, `updateItem(id, ...)`, `deleteItem(id)`, `categoriesFor(items)`. Screens pass RAW text; the service validates.
- `TableService`: `getTables()`, `countByStatus(list)`, `assignTable(tableId, name, phone)` -> new order id, `releaseTable(tableId)`.
- `TableDao`: `findAllWithCustomers()`, `assignTable(...)`, `releaseTable(...)`. `MenuDao`: `findAll()`, `insert`, `update`, `delete`.
- `Theme` colours/fonts, `Theme.statusColor(String)`; `DataTableStyle.apply(table, rightAlignedCols...)`, `DataTableStyle.BadgeRenderer`.
- `DatabaseConnection.getConnection()` reads `config/db.properties`.

## 9. Conventions

Java 21; packages lowercase, classes PascalCase, methods/fields camelCase, constants UPPER_SNAKE; short comment on every public class; friendly dialogs, never stack traces; screens keep no rules and no SQL. Git: `main` always builds, feature branches, PR + one review. Details in `docs/TEAM_AND_GIT_RULES.md`.

## 10. How to run

See README.md section 2 for the exact MySQL setup. Short version: install MySQL, run `database/schema.sql` then `database/sample_data.sql` (existing database: run `database/migrations/001_add_ready_order_status.sql` once), put the Connector/J jar in `lib/`, copy `config/db.properties.example` to `config/db.properties` and set the password, run `check-db.bat` (all `[ OK ]`), then `run.bat`. Persistence test: add a dish, close the app, start it again, the dish is still there.

Original notes:

JDK 21. `./build.sh` (Windows `build.bat`) -> `Build OK`; `./run.sh` (Windows `run.bat`). Works without MySQL (Dashboard demo data; Menu/Tables show a notice). With MySQL: run `database/schema.sql`, then `database/seed.sql`; put the Connector/J jar in `lib/` (see `lib/README.md`); copy `config/db.properties.example` to `config/db.properties` and set user/password. Optional tests: `dev-tests/README.md`.

## 11. Known issues

- **Phase 6: existing databases must also run `003_add_history_cleared_flag.sql` once** (until then the Clear button only explains this).
- **Existing databases must run `database/migrations/001_add_ready_order_status.sql` AND `002_add_billing_payment_columns.sql` once each** (001 is safe to repeat; 002 says "Duplicate column" if repeated) (Workbench: open, run). Until then READY cannot be saved.
- Tests ran on Dolt, not real MySQL (section 6).
- `DatabaseConnection` still prints an old hint mentioning `seed.sql` (it was left untouched on purpose; `seed.sql` is obsolete, use `sample_data.sql`). Harmless; fix only with the user's permission.
- The delivered Phase 4 ZIP has NO `config/db.properties` (privacy). The user's own file must stay in (or be copied to) `config/`. If the app says the settings file is missing after unzipping to a new folder, that is why.
- Billing: the GST default (5%) is a constant in `BillingService`; there is no settings screen for it. Printing was not exercised on a real printer. Receipt amounts use the rupee sign: it needs a font that has the glyph (Windows Monospaced/Consolas has it; if a box appears, tell the next Claude to switch the receipt to "Rs.").
- The Billing 'Paid bills' list shows only the latest 50; Order History shows at most 500 (newest first).
- Reports: Pending Payments counts SERVED orders created in the chosen period (not money). Best-selling "Sales" is before discount and GST (qty x unit price). Monthly report follows the chosen period (use "All time" to see every month). No CSV export. Charts are plain Java 2D; with very many days the value labels are hidden and the tooltip shows the value.
- Some narrow table cells in History / Reports clip long text (shown with "..") at the minimum window width; the window can be enlarged.
- The delivered ZIPs contain no `config/db.properties`; build scripts need `chmod +x *.sh` on Linux/macOS after unzipping.
- Release Table still cancels open orders even with food on them (by design, see section 4).
- Order list shows "Qty" = total pieces (2 burgers + 1 tea = 3).
- Menu and Tables screens show no demo rows when the DB is down (only the notice); only the Dashboard has demo data.
- Font is "Segoe UI"; other OSes substitute a font so spacing differs slightly.
- No JUnit tests (extra library, needs the user's approval); `dev-tests/` is a plain `main` program.
- Shell scripts may need `chmod +x build.sh run.sh` after unzipping.

## 12. Last completed milestone

**Reset Revenue feature** on top of **v1.0 Final refinement** (global Order History clearing + interactive UI). Tested on a MySQL-compatible server (NOT real MySQL); not yet run by the user on their MySQL.

## 13. EXACT NEXT MILESTONE

**First ask the user:** (1) after unzipping, did `build`/`run` work with their own `config/db.properties` copied in; (2) migration 003 already run? `check-db.bat` all `[ OK ]`; (3) migration 004 run and does Reset Revenue (Cancel, Confirm, new payment) behave on their MySQL; (3b) did Clear Order History now hide orders on Dashboard AND Order History on their MySQL (README section 7 + 7b); (4) screenshots of anything clipped or wrong. Fix what they report. Never print the password.
Optional only if asked: CSV export, GST setting screen, restore-cleared-orders button, removing obsolete `database/seed.sql` and the stale hint in `DatabaseConnection` (needs permission). Preserve every protected file.

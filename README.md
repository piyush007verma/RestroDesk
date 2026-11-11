# RestoDesk - Restaurant Billing and Table Management System

College Java project (Java 21, Swing, MySQL, JDBC). Team of 4. Current version: **v1.0 Final** (Phase 6 + final refinement).

Windows: use the `.bat` files. Linux / macOS / Git Bash: use the same names with `.sh` (for example `./run.sh`).

## 0. Project overview

**Problem.** A small restaurant that works on paper loses track of which tables are free, which dishes were ordered, how much
GST and discount were applied, and how much money came in each day. Bills are calculated by hand and mistakes are common.

**Objectives.** One desktop program that (1) keeps the menu, (2) seats customers on tables, (3) takes and tracks orders from the
kitchen to the table, (4) calculates the bill with GST and discount and records the payment, (5) frees the table by itself after
payment, (6) lets the owner search old orders, and (7) shows sales reports. Everything is stored in MySQL, so nothing is lost when
the program is closed.

**Technology stack.** Java 21, Swing (window and screens), JDBC with `PreparedStatement`, MySQL 8, MySQL Connector/J (jar in `lib/`).
No framework and no build tool: `build.bat` / `run.bat` use plain `javac` and `java`. (Swing is not in our course handout; the rest of
the code - collections, exceptions, JDBC, streams, generics - follows it.)

**Architecture.** `Swing screen (ui)  ->  Service  ->  DAO interface  ->  DAO implementation (JDBC)  ->  MySQL`.
Screens never write SQL; services hold the rules (validation, GST, status flow); DAOs hold the SQL. Slow database calls run in
`ui/Background` (a `SwingWorker` wrapper) so the window never freezes. `db/DatabaseConnection` is the ONLY place that opens a connection.

**Folders.**
```
config/    db.properties (YOUR private settings, not in the ZIP)   db.example (template)
lib/       mysql-connector-j-*.jar
database/  schema.sql  sample_data.sql  migrations/001, 002, 003, 004
src/com/restaurant/
   app/ (Main)   ui/ (screens, Theme, Background, Messages)   ui/components/ (buttons, cards, chart, badges)
   service/      dao/ (interfaces + *Impl)     model/ (records, enums)   exception/   db/ (DatabaseConnection, DbCheck)
dev-tests/ developer tests (not part of the app)     docs/ ARCHITECTURE.md, TEAM_AND_GIT_RULES.md
README.md  VIVA_PREPARATION.md  TESTING_REPORT.md  HANDOFF.md  NEXT_CLAUDE_PROMPT.txt
docs/screenshots/   PNG pictures of every screen
build.bat run.bat check-db.bat (+ .sh versions)
```

**Features.** Dashboard (live counts and revenue) - Menu management (add/edit/delete/availability, category filter) - Table
management (assign a customer, release) - Orders (add dishes, quantity, Send to Kitchen / Ready / Served, cancel) - Billing (GST,
percentage or fixed discount, Cash/UPI/Card, receipt, print) - Order History (search, filters, details, receipt, **Clear Order
History**) - Reports (periods, daily/monthly sales, best sellers, payment split).

**Typical workflow.** Seat customer (table becomes Busy) -> add dishes -> kitchen -> Served -> Billing -> Confirm Payment (bill saved,
order PAID, table Free again - all in one transaction) -> shows in Order History and Reports.

## 1. Run the app (needs JDK 21)

`run.bat` - builds the project and starts it. The Dashboard works even without MySQL (it then shows labelled demo data),
but Menu and Tables need MySQL (section 2).

In IntelliJ / VS Code: open this folder, mark `src` as the source root, add every `.jar` in `lib/` as a library,
run `com.restaurant.app.Main`. Make the run configuration's working directory the project folder.

## 2. Connect MySQL (one time)

1. **Install MySQL** (MySQL Community Server 8.x; MySQL Workbench is handy). Remember the root password you choose.
   Make sure the MySQL service is running (Windows: Start > Services > MySQL80 > Start).
2. **Create the database and tables.** In MySQL Workbench: connect, File > Open SQL Script, choose `database/schema.sql`,
   click the lightning-bolt button. Then do the same with `database/sample_data.sql` (sample data).
   Or in a terminal inside the project folder: `mysql -u root -p < database\schema.sql` and then `mysql -u root -p < database\sample_data.sql`.
   `schema.sql` is safe to run again: it never deletes anything (`seed.sql` is obsolete, use `sample_data.sql`).
3. **Add the MySQL driver.** Download "MySQL Connector/J" (Platform Independent ZIP) from
   https://dev.mysql.com/downloads/connector/j/ and copy the `mysql-connector-j-x.y.z.jar` file into the `lib` folder.
4. **Create your settings file.** Copy `config\db.example` (or `db.properties.example` in older ZIPs) to `config\db.properties`
   (Windows: `copy config\db.properties.example config\db.properties`), open it in Notepad and put YOUR MySQL password.
   This file is ignored by Git, so your password never goes to GitHub. Make sure it is not saved as `db.properties.txt`.
5. **Test the connection:** run `check-db.bat`. Every line must start with `[ OK ]`. If a line says `[FAIL]` it explains how to fix it.
6. **Start the app:** `run.bat`. The Menu screen should now list the 9 sample dishes.

**Already have a `restodesk` database from an earlier phase?** Run these once, in order, in MySQL Workbench
(File > Open SQL Script > lightning bolt). Your data stays; they only ADD things:
1. `database/migrations/001_add_ready_order_status.sql` (adds the order status READY; safe to run twice) - if you did not run it in Phase 3.
2. `database/migrations/002_add_billing_payment_columns.sql` (adds 3 columns to `bills` for Phase 4). Run it ONCE: a second run only says "Duplicate column name", which is harmless.
3. `database/migrations/003_add_history_cleared_flag.sql` (Phase 6: adds ONE nullable column `orders.history_cleared_at`, used by Clear Order History). Run it ONCE; a second run only says "Duplicate column name". Until you run it the whole app works and the Clear button only explains this step.

To confirm data is really saved in MySQL: add a dish, close the app, start it again - the dish must still be there.

## 3. Problems? Run `check-db.bat` first

| Message in the app or in check-db | Meaning | Fix |
|---|---|---|
| settings file config/db.properties was not found | file missing, misnamed or hidden `.txt` | copy the example file, step 4 |
| still contains the example password | password not changed | edit `config\db.properties` |
| MySQL driver is missing | no jar in `lib` | step 3 |
| MySQL rejected the user name or password | wrong `db.user` / `db.password` | test them in Workbench |
| Cannot reach the MySQL server | MySQL not running, or wrong port | start the MySQL service; port is normally 3306 |
| database does not exist yet / table missing | scripts not run | step 2 |

The orange notice in the app shows the same message, plus the real technical error in square brackets.

## 4. Phase 3 checklist (Order Management)

1. `check-db.bat` -> all `[ OK ]` (including `orders.status accepts READY`). If not, run the migration above.
2. `run.bat`. Tables > assign a free table (name required). This creates the order.
3. Orders > click the order > click a dish in the Menu card > Quantity > `Add to Order`. Add a second dish.
4. Select a cart row: `+`, `-`, `Remove`. Check the subtotals and the total.
5. `Send to Kitchen` -> `Mark Ready` -> `Mark Served`. Close the app, start it again: order, items and status are still there.
6. Menu > use the category drop-down above the table; add a dish with a new category, restart, it is still there.

## 5. Phase 4 checklist (Billing and payment)

1. `check-db.bat` -> all `[ OK ]` (including `bills has the payment columns`). If not, run migration 002 (section 2).
2. `run.bat`. Tables: seat a customer. Orders: add food, then `Send to Kitchen` -> `Mark Ready` -> `Mark Served`. The table is still Busy.
3. Billing: only SERVED orders are listed. Click one: items and the bill appear. Try a discount (percentage or fixed) and a different GST %. Wrong values (letters, negative, 150 %) show a message and disable payment.
4. Choose Cash / UPI / Card and press `Confirm Payment`, then answer **No**: nothing changes, the table is still Busy.
5. Press `Confirm Payment` again and answer **Yes**: "Payment successful", the receipt opens (Print button = Windows print dialog; choose "Microsoft Print to PDF" for a PDF). Now the table turns Free (Tables screen) and Dashboard revenue grows.
6. Billing > drop-down `Paid bills` > click the bill > `View Receipt`. Close and start the app again: the bill is still there.

The GST default (5 %) is the constant `DEFAULT_GST_PERCENT` in `src/com/restaurant/service/BillingService.java`; you can also type any value from 0 to 100 in the GST box.

## 6. Phase 5 checklist (Order History and Reports)

No database change is needed for Phase 5.
1. Open **Order History** from the sidebar. You should see every order you made (any status). Try the filters (Order ID, Table number, Status, Payment, Method, date range) and `Reset`.
2. Click an order: its items, discount, GST, grand total and payment appear on the right. `View Receipt` works for paid orders.
3. Open **Reports**. Choose Today / Last 7 days / This month / All time / Custom range. Total Revenue must equal the sum of your PAID bills.
4. Daily and monthly tables, the best-selling items and the Cash / UPI / Card split update with the period. History and Reports only read data; nothing can be edited or deleted there.

## 7. Phase 6 checklist (Clear Order History)

1. Run migration 003 once (section 2), then `check-db.bat` -> the new line `orders has history_cleared_at` must be `[ OK ]`.
2. Make an order, serve it, pay it in Billing. It appears in **Order History** and its amount is in **Reports**.
3. Order History > red **Clear Order History** button. A dialog says how many finished (paid / cancelled) orders will leave the list.
   Press **Cancel** (the default button): nothing changes. Press the button again and choose **Clear History**: a success message appears and the list refreshes.
4. Open Reports and Billing > Paid bills: revenue, GST, discounts, payment split, bills and receipts are exactly as before.
5. Tick **Show cleared orders** in Order History: the cleared orders come back, marked with `*`. Close and start the app: nothing is restored or lost.

**How it protects money data.** Clearing never deletes anything. It sets a timestamp (`orders.history_cleared_at`) on finished orders only
(PAID or CANCELLED); Order History hides rows that have the timestamp. `bills`, `order_items`, Reports and the Dashboard do not use that
column at all. Open orders (pending / preparing / ready / served) are never cleared.

## 7b. Final refinement (v1.0): one shared history rule + interactive UI

**Why Dashboard still showed cleared orders (root cause).** Phase 6 added the hiding rule only inside the Order History query. The Dashboard has its own
queries (Recent Orders list and Total Orders card) that never looked at `history_cleared_at`, so they kept listing cleared orders.
**Fix.** One shared class, `dao/HistoryVisibility`, holds the rule ("hide orders whose `history_cleared_at` is set; do nothing before migration 003").
Order History and the Dashboard both ask it. Money screens (Reports, Billing, revenue) deliberately do not use it. The flag is stored in MySQL,
so it survives restarts, and orders created after clearing have no flag, so they appear normally. No new migration is needed (migration 003 from Phase 6 is enough).
Orders and Tables only show OPEN orders, which are never cleared, so they cannot disagree. On the Dashboard "Total Orders" now means "orders still in the history list";
"Total Revenue" always counts every paid bill.

**What is new in the interface**
- Sidebar: drawn icons, hover and active highlight, tooltips and shortcuts **Alt+1 ... Alt+7** (Dashboard, Menu, Tables, Orders, Billing, Order History, Reports), Tab focus ring.
- Dashboard: clickable cards (Orders -> Order History, Revenue -> Reports, Tables -> Tables), quick-action buttons (New Order, Add Dish, Take Payment, View Reports),
  "Revenue - last 7 days" chart from real bills, double-click a recent order to open History, empty-state text.
- Menu and Orders: live search boxes (Esc clears). All main lists: click a column header to sort (numbers sort as numbers).
- Buttons: hover and pressed look, keyboard focus ring. A thin amber loading bar shows while the database is being read or written.
- Empty states ("No orders yet...", "No dish matches your search."), consistent dialog and tooltip fonts, wider History columns, long amounts shrink instead of being cut off.
- Screenshots of all screens: `docs/screenshots/`.

## 7c. Reset Revenue (new)

A red **Reset Revenue** button on **Reports** (next to Apply) and on the **Dashboard** (quick-action row). Both use the same code and show the same note
("Revenue counted since <date, time>"). It starts the revenue and amount figures again from zero **without deleting anything**.

**One-time database step:** run `database/migrations/004_add_revenue_resets.sql` once in MySQL Workbench (File > Open SQL Script > lightning button).
It only CREATES one new empty table, `revenue_resets` (safe to run again). Until you run it the whole app works as before and the button explains this step. `check-db.bat` shows a new line `revenue_resets table exists`.

**What is reset** (shown as zero from now on): Total Revenue, payments done, average order value, GST collected, discounts given, daily and monthly sales, payment-method split, best-selling items - on Dashboard (revenue card and 7-day chart) and in Reports, together.
**What stays exactly as it is:** every bill, payment, GST and discount record, receipts, the Billing "Paid bills" list and Order History. The order counts in Reports (Total Orders, Cancelled) are not money: they are NOT changed by Reset Revenue, but they DO follow Clear Order History (cleared orders are not counted, same as on the Dashboard).
**How it works:** each reset adds ONE row to `revenue_resets` (time, number of the last bill at that moment, how many bills/how much money were cleared). Revenue queries add one condition: count only bills newer than the latest row (`dao/RevenueBaseline`). New payments after the reset have newer bill numbers, so they count normally.
**Undo:** delete the rows of `revenue_resets` and the old totals are back (no bill was ever changed).

**How to test:** (1) pay two or three orders; note revenue on Dashboard and Reports. (2) Click **Reset Revenue**: the dialog explains what is reset and what stays. Press **Cancel** (the default): nothing changes. (3) Click it again and choose **Reset Revenue**: success message; revenue shows 0 on Reports and Dashboard. (4) Billing > Paid bills and Order History still list everything. (5) Pay a new order: it shows as the new revenue on both screens. (6) Restart the app: still the same figures.

## 8. Known limitations

- Tested here only on a MySQL-compatible server (Dolt), not on real MySQL, and not on Windows `run.bat` or the Windows print dialog (see TESTING_REPORT.md). Your own MySQL 8.0.46 check in Phase 4 was fine.
- Clear Order History hides orders; it cannot permanently erase them (that is deliberate, to keep bills and reports correct). There is no "undo" button, but "Show cleared orders" shows them again.
- The Dashboard shows clearly labelled demo numbers only when the database cannot be reached (from Milestone 1); nothing is saved from them.
- Search boxes exist on Menu and Orders (History has its own filter row); sorting is by clicking column headers. There is no animation.
- Reset Revenue is not an undo-able button in the app (a developer can delete the rows of `revenue_resets`); the Reports date filters count only bills newer than the latest reset, so a period before the reset shows zero.
- Single computer, single user: no login, no multi-user locking beyond the database transactions.
- GST is one percentage per bill (default 5 %, editable); there is no per-dish GST.

See `docs/ARCHITECTURE.md` and `docs/TEAM_AND_GIT_RULES.md`. Project progress and handover: `HANDOFF.md`.

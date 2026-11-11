# RestoDesk - Viva Preparation

Everything below describes code that really exists in this project. Swing is NOT in the course handout (CHO) - say so honestly if asked; the rest (OOP, collections, exceptions, JDBC, streams) is.

## 1. Project introduction
RestoDesk is a desktop program for a small restaurant: menu, tables, orders, billing with GST and discount, payment, order history and sales reports. Java 21 + Swing screens, MySQL database, JDBC to talk to it.

## 2. Problem statement
Manual restaurants lose track of free tables, calculate bills by hand (GST/discount mistakes) and cannot easily see sales. RestoDesk automates this and stores everything permanently.

## 3. Objectives
Keep menu data - seat customers - track orders PENDING -> PREPARING -> READY -> SERVED - bill with GST/discount - record payment - free table automatically - search history - show reports - never lose financial data.

## 4. Technology stack
Java 21, Swing, JDBC (`PreparedStatement`), MySQL 8, MySQL Connector/J jar in `lib/`, plain `javac`/`java` scripts (`build.bat`, `run.bat`, `check-db.bat`). No Spring, Hibernate or Maven.

## 5. Architecture and workflow
Layers: **ui (Panel) -> service -> dao interface -> dao implementation -> MySQL**. Panels show data and read input; services validate and apply rules; DAOs contain all SQL. `MainFrame.registerScreens()` creates every service/DAO and connects the screens. `DatabaseConnection.getConnection()` is the single place that reads `config/db.properties` and opens a connection. Slow calls go through `Background.run(task, onSuccess, onError)`.
Workflow: Tables (assign) -> Orders (add dishes, change status) -> Billing (pay) -> Order History -> Reports.

## 6. Modules
- **Dashboard** (`DashboardPanel/Service/Dao`): table counts, active orders, today's revenue, recent orders.
- **Menu** (`MenuPanel`, `MenuService`, `MenuDao`): add/edit/delete dishes, availability, category filter; a dish used in orders cannot be deleted.
- **Tables** (`TablesPanel`, `TableService`, `TableDao`): assign a free table (creates an order) and release.
- **Orders** (`OrdersPanel`, `OrderService`, `OrderDao`): cart of dishes, quantity, status flow.
- **Billing** (`BillingPanel`, `BillingService`, `BillDao`): bill calculation, payment, receipt.
- **Order History** (`HistoryPanel`, `HistoryService`, `HistoryDao`): search/filters, details, receipt, Clear Order History.
- **Reports** (`ReportsPanel`, `ReportsService`, `ReportsDao`): period summary, daily/monthly tables, best sellers, payment split, bar chart.

## 7. Important classes and methods
`Main` (starts the UI on the Event Dispatch Thread) - `MainFrame` (window, sidebar, `navigateTo`) - `DatabaseConnection.getConnection()/loadConfig()` - `Background.run()` - `Messages` (dialogs) - `BillCalculation` (subtotal, discount, GST, grand total) - `BillingService.calculate()/pay()` - `BillDaoImpl.payOrder()` (transaction) - `TableDaoImpl.assignTable()` (transaction) - `HistoryDaoImpl.search()/clearClosedOrders()` - `ReportsService.resolve()/load()` - `DbCheck` (connection diagnostics).

## 8. OOP concepts (with real examples)
- **Abstraction / interfaces:** `MenuDao`, `TableDao`, `OrderDao`, `BillDao`, `HistoryDao`, `ReportsDao`, `DashboardDao` are interfaces; `*DaoImpl` classes implement them. Services depend on the interface.
- **Inheritance:** every screen `extends ScreenPanel` (abstract class); `RoundedButton extends JButton`, `StatCard extends RoundedPanel`.
- **Encapsulation:** private fields with getters (`MenuItem`, `OrderSummary`); `DatabaseConnection` has a private constructor/static methods.
- **Polymorphism:** `MainFrame` stores screens as `ScreenPanel`; the service holds a `HistoryDao` that may be any implementation (the tests use fake DAOs).
- **Enums:** `OrderStatus`, `TableStatus`, `PaymentMethod`, `DiscountType` (with labels).
- **Records:** `HistoryFilter`, `HistoryRow`, `TopItem`, `MethodTotal` - immutable data carriers.
- **Generics / functional interfaces:** `Background.run(Callable<T>, Consumer<T>, Consumer<Exception>)`.
- **Custom exceptions:** see section 10.

## 9. Collections
`ArrayList` (rows from the database, cart lines), `List.of` (immutable lists), `EnumMap` (counts per status), `LinkedHashMap` (ordered maps, e.g. the table list in `DbCheck`), `HashSet`, `Optional`, and streams with `Collectors.groupingBy/counting/toList`.

## 10. Exception handling
Checked custom exceptions: `DatabaseException` (anything wrong with MySQL; the message says how to fix it), `InvalidInputException` (bad user input or an order that cannot be billed), `TableUnavailableException` (table taken by someone else). DAOs catch `SQLException` and rethrow `DatabaseException` with a friendly message; `Background` delivers the error to `Messages.error(...)` on the UI thread, so the app never crashes on a database problem. try-with-resources closes connections, statements and result sets; `finally` rolls back unfinished transactions.

## 11. JDBC and database operations
Steps: load config -> `DriverManager.getConnection` (inside `DatabaseConnection`) -> `PreparedStatement` with `?` parameters -> `executeQuery/executeUpdate` -> read `ResultSet` -> close (try-with-resources). Prepared statements stop SQL injection. Transactions: `setAutoCommit(false)`, `commit()`, `rollback()` (assign table, pay order, clear history). `SELECT ... FOR UPDATE` locks the order while paying. `DECIMAL(10,2)` and `BigDecimal` are used for money, never `double`.

## 12. Multithreading actually used
Swing is single-threaded (Event Dispatch Thread). Database work runs in a `SwingWorker` (`Background`, also `DashboardPanel`): `doInBackground()` does the query, `done()` updates the screen on the UI thread. This is why the window never freezes. There are no hand-made threads, `synchronized` blocks or thread pools in the project - do not claim them.

## 13. Database tables
`restaurant_tables(table_id, table_number, capacity, status)` - `menu_items(item_id, name, category, price, available)` - `orders(order_id, table_id FK, customer_name, customer_phone, status ENUM, created_at, history_cleared_at)` - `order_items(order_item_id, order_id FK ON DELETE CASCADE, item_id FK, quantity, unit_price)` - `bills(bill_id, order_id UNIQUE FK, subtotal, gst_percent, gst_amount, discount_type, discount_value, discount_amount, total_amount, payment_method, payment_status, paid_at)`.
Relationships: one table -> many orders; one order -> many order_items; one order -> at most one bill (UNIQUE); one menu item -> many order_items. `unit_price` is copied into `order_items` so old orders keep their price if the menu price changes.

## 14. Billing and payment
Subtotal = sum(quantity x unit price). Discount = percent of subtotal or a fixed amount (cannot exceed the subtotal). Taxable = subtotal - discount. GST = taxable x GST% / 100 (default 5 %). Grand total = taxable + GST. All rounded `HALF_UP` to 2 decimals. Only SERVED orders can be billed. On "Confirm Payment" (after a Yes/No confirmation) `BillDaoImpl.payOrder` runs ONE transaction: lock order -> check status and no existing bill -> insert bill -> set order PAID -> set table AVAILABLE -> commit. Any failure rolls back, so the table stays occupied and no half-bill exists. A second payment is refused (status check and `UNIQUE(order_id)`).

## 15. Automatic table release
Step 4 of the payment transaction frees the table. Because it is inside the same transaction as the bill, a failed payment can never release the table (tested by simulating a failure in the last step). Cancelling an order from Tables also releases it.

## 16. Clear Order History (global, one shared rule)
Button in Order History -> counts finished orders (PAID/CANCELLED) -> confirmation dialog (Clear History / Cancel, Cancel is default) -> `UPDATE orders SET history_cleared_at = NOW() ...` in a transaction -> success message and refresh. It is a **logical clear**: nothing is deleted. Why: `DELETE FROM orders` would cascade to `order_items` and break the link to `bills`, destroying revenue/GST/discount history. Bills, payments, Reports and Dashboard never read `history_cleared_at`, so they are unchanged (proved by comparing every bill column, every report number and receipts before/after, and after restarting). "Show cleared orders" lists them again. Migration 003 adds the column; without it the button just explains the migration.
**Global rule:** `dao/HistoryVisibility.visibleOnly(con, "o")` returns `AND o.history_cleared_at IS NULL` (or nothing before the migration). `HistoryDaoImpl` and `DashboardDaoImpl` (Recent Orders + Total Orders) both call it, so every list that shows past orders hides the same orders. The first version only filtered inside the History query, which is why the Dashboard still showed them (root cause). Reports/Billing never call it, so money is never affected. The column check is cached in a `static volatile boolean` once it is true.

## 16b. Interface features added in the final refinement
`SidebarButton.drawIcon` draws icons with `Graphics2D` (no image files). `StatCard.setOnClick` makes a card clickable (MouseListener + key binding). `DataTableStyle.enableSorting` installs a `TableRowSorter` with a comparator that sorts numbers numerically; `filterRows` uses `RowFilter.regexFilter` for the search boxes; `selectedModelRow` / `selectModelRow` convert between the row on screen and the row in the list (`convertRowIndexToModel/View`) - needed because sorting changes the order on screen. `SearchField` is a `JTextField` with a hint and a `DocumentListener`. `Background` counts running tasks and tells `MainFrame` to show a `JProgressBar` loading bar. Alt+1..7 use the window's `InputMap` / `ActionMap` (key bindings). `Dashboard` gets a `Consumer<Screen>` navigator (a functional interface) so cards and buttons can open screens without knowing `MainFrame`.

## 16c. Reset Revenue
Button on Reports and Dashboard (both call `RevenueResetAction.run`). It never deletes bills. Each reset INSERTs one row into `revenue_resets` (time, `last_bill_id`, bills and amount cleared) inside a transaction. `RevenueBaseline.countedBillsOnly` adds `AND b.bill_id > (latest last_bill_id)` to every revenue query in `ReportsDaoImpl` and to the Dashboard revenue, so both screens agree; bills paid later have a higher bill number and count normally. Using the bill number instead of the time avoids errors when two events happen in the same second. Migration 004 only creates the new table.

## 17. Viva questions and answers (54 questions)

1. **What is RestoDesk?** A Java Swing + MySQL restaurant billing and table management program.
2. **Why separate layers?** Easier to change/test; SQL is only in DAOs, rules only in services, screens only show data.
3. **What is a DAO?** Data Access Object: a class that hides SQL behind methods like `search()`.
4. **Why DAO interfaces?** Services depend on the interface, so a fake DAO can be used in tests (M2Test).
5. **What is JDBC?** Java's standard API to connect to databases: Connection, PreparedStatement, ResultSet.
6. **Why PreparedStatement?** Prevents SQL injection and handles types safely; reusable.
7. **How do you avoid resource leaks?** try-with-resources closes Connection/Statement/ResultSet automatically.
8. **Where is the connection made?** Only `DatabaseConnection.getConnection()`, using `config/db.properties`.
9. **What is a transaction?** A group of SQL steps that all succeed (commit) or all undo (rollback).
10. **Where do you use transactions?** Assign table (claim table + create order), pay order, clear history.
11. **What happens if payment fails halfway?** Rollback: no bill, order not PAID, table still occupied.
12. **How is double payment prevented?** Status check under `FOR UPDATE` plus `UNIQUE(order_id)` in `bills`.
13. **Why BigDecimal for money?** `double` has rounding errors; BigDecimal with `RoundingMode.HALF_UP` is exact.
14. **How is GST calculated?** (subtotal - discount) x GST % / 100, default 5 %.
15. **Which discount types exist?** NONE, PERCENT, FIXED (`DiscountType` enum).
16. **Which orders can be billed?** Only SERVED ones.
17. **Order status flow?** PENDING -> PREPARING -> READY -> SERVED -> PAID, or CANCELLED.
18. **How does automatic table release work?** Inside the payment transaction the table is set to AVAILABLE.
19. **How are two cashiers stopped from taking the same table?** `UPDATE ... WHERE status = 'AVAILABLE'`; if 0 rows change a `TableUnavailableException` is thrown.
20. **What is a SwingWorker?** A Swing class to run slow work in the background and update the UI on the UI thread.
21. **What is `Background`?** Our small generic wrapper around SwingWorker (`Callable<T>`, `Consumer<T>`).
22. **Why not query the database on the UI thread?** The window would freeze.
23. **What is the Event Dispatch Thread?** The single thread that draws and handles Swing events; `Main` starts the UI on it.
24. **Do you use multithreading?** Only SwingWorker background threads; no manual threads or synchronized code.
25. **What OOP concepts are used?** Interfaces, abstract class (`ScreenPanel`), inheritance, encapsulation, polymorphism, enums, records.
26. **Example of inheritance?** `OrdersPanel extends ScreenPanel`; `RoundedButton extends JButton`.
27. **Example of polymorphism?** `MainFrame` holds `ScreenPanel` references to different screens; services use `Dao` interfaces.
28. **What is a record?** A compact immutable class, e.g. `HistoryFilter`, `TopItem`.
29. **Which collections are used?** ArrayList, List.of, EnumMap, LinkedHashMap, HashSet, Optional; streams with Collectors.
30. **What are streams used for?** Grouping and counting, e.g. `Collectors.groupingBy/counting`, filtering lists.
31. **Which custom exceptions?** DatabaseException, InvalidInputException, TableUnavailableException.
32. **What is `check-db.bat`?** `DbCheck`: tests settings, password, driver, connection, tables and migrations step by step.
33. **What does the History screen do?** Searches orders with filters (ID, table, status, payment, method, dates) using one SQL with dynamic `AND ... = ?` parts.
34. **What is Clear Order History?** A button that hides finished orders from the list without deleting anything.
35. **Why not `DELETE FROM orders`?** `order_items` has ON DELETE CASCADE and `bills` points to orders; deleting would destroy items, bills, revenue and GST history.
36. **How does clearing work then?** Sets `history_cleared_at = NOW()` for PAID/CANCELLED orders; History filters on `IS NULL`.
37. **Which orders are never cleared?** Pending, preparing, ready and served ones.
38. **Does clearing change Reports?** No: Reports read `bills`/`order_items`, never the flag; tested with identical report numbers before/after and after restart.
39. **How can cleared orders be seen again?** "Show cleared orders" checkbox (they are marked `*`).
40. **What if the migration was not run?** The app works; the button shows a message naming migration 003; `check-db` shows `[FAIL]` with the fix.
41. **What does Cancel do in the confirmation dialog?** Nothing is written; Cancel is also the default button.
42. **How are reports calculated?** `ReportsDaoImpl` runs SUM/COUNT/GROUP BY queries on paid `bills` for the chosen date range.
43. **Limitations?** Single user, no login, one GST % per bill, tested on a MySQL-compatible server and your own MySQL, Windows printing not tested in the sandbox.
44. **Why did the Dashboard still show cleared orders at first?** The hiding condition lived only in the Order History query; the Dashboard's own SQL did not have it.
45. **How did you fix it?** One shared class `HistoryVisibility` that both DAOs use; Reports and Billing do not use it.
46. **Why not just filter in the screens?** The data must be filtered in SQL so every query agrees and the Java code stays simple; screens must not hold rules.
47. **Why does sorting need `convertRowIndexToModel`?** After sorting, row 0 on screen may be row 5 of the data list; we must convert before reading the list.
48. **How does the live search work?** A `DocumentListener` calls `filterRows`, which sets a `RowFilter` on the table's `TableRowSorter`.
49. **How do the dashboard cards navigate?** The panel receives a `Consumer<Screen>` (here `MainFrame::navigateTo`) and calls `accept(Screen.HISTORY)` on click.
50. **How does the loading bar know the app is busy?** `Background.run` counts started and finished SwingWorkers and informs `MainFrame`, which shows or hides a `JProgressBar`.
51. **How does Reset Revenue work without deleting bills?** It saves a reset row with the last bill number; revenue queries count only newer bills, so old bills stay in the database.
52. **Why the bill number and not a timestamp?** A bill number is exact; two events in the same second could be counted wrongly with a timestamp.
53. **How do Dashboard and Reports stay consistent?** Both use the same condition from `RevenueBaseline` and the same `ReportsService`.
54. **How is accidental reset prevented?** A confirmation dialog explains what changes, Cancel is the default button, and nothing is written until "Reset Revenue" is chosen.

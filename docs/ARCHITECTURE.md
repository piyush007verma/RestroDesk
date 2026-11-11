# RestoDesk - Architecture

## 1. The big idea (simple version)

The app is built in **layers**. Each layer only talks to the layer directly below it:

```
 Swing screens (ui)      what the user sees and clicks
        |
 Services (service)      business rules: totals, GST, validation
        |
 DAOs (dao)              the only code that writes SQL (JDBC + PreparedStatement)
        |
 MySQL database
```

`model` classes (MenuItem, Order ...) are plain Java objects passed between layers.
`exception` holds our custom exceptions. `db` opens the connection.

Why layers? Four people can work at once: Member 2 can build the Orders screen without
touching SQL, and Member 3 can change a query without breaking any screen.

## 2. Package structure

```
src/com/restaurant/
  app/        Main.java                 program entry point
  ui/         MainFrame, Screen, Theme, ScreenPanel, *Panel, Background, Messages   (Swing)
  ui/components/  RoundedPanel, RoundedButton, StatCard, StatusBadge, SidebarButton, DataTableStyle
  service/    DashboardService, MenuService, TableService      business logic
  dao/        DashboardDao, MenuDao, TableDao (interfaces) + the matching *DaoImpl (JDBC)
  model/      RestaurantTable, TableStatus, RecentOrder, DashboardData, MenuItem, TableOverview ...
  db/         DatabaseConnection        the only place that opens a connection
  exception/  DatabaseException, InvalidInputException, TableUnavailableException   custom exceptions
database/     schema.sql, seed.sql
config/       db.properties.example     (real db.properties is git-ignored)
docs/         architecture, team and git rules
```

## 3. How a click travels (example: opening the Dashboard)

1. User clicks "Dashboard" in the sidebar -> `MainFrame.navigateTo(Screen.DASHBOARD)`.
2. `DashboardPanel.onShow()` starts a `SwingWorker` (background thread, so the window never freezes).
3. The worker calls `DashboardService.loadDashboard()`.
4. The service calls `DashboardDao` methods; `DashboardDaoImpl` runs `PreparedStatement` queries.
5. Results come back as model objects -> `DashboardPanel.render()` draws them.
6. If MySQL is not reachable, the service returns clearly labelled demo data instead of crashing.

Every future feature follows this same pattern: **Panel -> Service -> Dao (interface) -> DaoImpl -> MySQL**.

## 4. Database schema

Database `restodesk`, five tables (see `database/schema.sql`):

| Table | Purpose | Key columns |
|---|---|---|
| restaurant_tables | physical tables | table_id, table_number, capacity, status (AVAILABLE/OCCUPIED) |
| menu_items | food menu | item_id, name, category, price, available |
| orders | one customer sitting | order_id, table_id (FK), customer_name, status, created_at |
| order_items | foods in an order | order_id (FK), item_id (FK), quantity, unit_price |
| bills | payment record | order_id (FK, unique), subtotal, gst_percent, gst_amount, discount_amount, total_amount, payment_method |

Relationships: table 1-* orders; orders 1-* order_items *-1 menu_items; orders 1-1 bills.
`unit_price` is copied into `order_items` so later menu price changes never alter old bills.
Order status flow: PENDING -> PREPARING -> SERVED -> PAID (or CANCELLED).

## 5. Where each course concept will be used (CHO alignment)

| Concept | Where |
|---|---|
| Classes, constructors, encapsulation | all `model` classes (private fields + getters) |
| Interfaces and abstraction | `DashboardDao` / `...DaoImpl`; same for every DAO |
| Inheritance and polymorphism | `ScreenPanel` -> `PlaceholderScreen`/real panels; `Screen` enum drives navigation |
| Collections (ArrayList, HashMap, EnumMap) | DAO result lists; cart in Orders (HashMap item->qty); `MainFrame` EnumMaps |
| Exceptions and custom exceptions | `DatabaseException`, `InvalidInputException` (bad user input), `TableUnavailableException` (table already taken / not occupied); `Messages` turns each into a friendly dialog |
| JDBC and PreparedStatement, CRUD | every `*DaoImpl` |
| Transactions | `TableDaoImpl.assignTable` (occupy table + create PENDING order) and `releaseTable` (free table + cancel open order): commit together or roll back. Billing (Member 3) will do the same for bill + PAID + release |
| Lambdas and functional interfaces | button listeners, `forEach` in MainFrame |
| Stream API and Collectors | `DashboardService` and `TableService.countByStatus` (groupingBy + counting), `MenuService.categoriesFor`; Reports later (group sales, popular items) |
| Generics | `Background.run(Callable<T>, Consumer<T>, Consumer<Exception>)`; optional generic `BaseDao<T>` later (ask the team first) |
| File handling | export a bill / report to a .txt or .csv file (Member 3/4) |
| Multithreading and synchronization | `SwingWorker` for DB loading now; synchronized table assignment later, only if it fits naturally |

Swing is used for the experimental desktop version and is **not** part of the CHO syllabus.
Business logic, collections, exceptions, JDBC and the other items above are the CHO-aligned core.
Two optional extras are NOT used and need your approval first: any build tool (Maven/Gradle), any extra library besides the MySQL driver.

## 6. Milestone 2 decisions (Menu and Table management)

- **No schema change.** Assigning a table = ONE `orders` row with status PENDING (customer name and phone) + `restaurant_tables.status = OCCUPIED`, in one JDBC transaction (`TableDaoImpl.assignTable`).
- **No double booking:** the first statement is `UPDATE restaurant_tables SET status='OCCUPIED' WHERE table_id=? AND status='AVAILABLE'`. If it changes 0 rows, someone else took the table -> `TableUnavailableException`, nothing is saved, the screen refreshes.
- **Release** (`TableDaoImpl.releaseTable`) frees an OCCUPIED table and marks any open order (PENDING, PREPARING, SERVED) on it as CANCELLED, in one transaction. The screen asks for confirmation first. After payment, Billing (Member 3) frees the table itself and marks the order PAID instead.
- **Contract for Order Management (Milestone 3):** a seated table already has its PENDING order. Look it up through `TableService.getTables()` -> `TableOverview.getOpenOrderId()` (or `orders` by `table_id` and open status) and ADD `order_items` to it. Never create a second order for a seated table.
- **Menu delete:** if the dish is used by `order_items` the database refuses (foreign key); `MenuDaoImpl` turns that into a friendly message that tells the user to untick "Available" instead. Duplicate names (case-insensitive, thanks to the `utf8mb4_unicode_ci` collation) get a friendly message too.
- **Background work:** every database call from a screen goes through `Background.run(...)` (a generic SwingWorker wrapper). Screens never touch the database on the Swing thread.
- **No database:** Menu and Tables show an orange "Database not connected" notice (hover for the technical reason) and stay empty; the Dashboard still shows its demo data.

## Phase 3 - Order Management (added)

Panel -> Service -> Dao -> DaoImpl as before: `OrdersPanel` -> `OrderService` -> `OrderDao` -> `OrderDaoImpl`.
- `OrderService` holds the rules (quantity 1..99, dish must be available, one step at a time in the status flow) and calculates totals with the Stream API (`map` + `reduce`).
- `OrderDaoImpl` repeats the database-dependent checks inside ONE transaction (`SELECT ... FOR UPDATE`, then commit or rollback), so two computers cannot break an order: closed orders, dishes made unavailable meanwhile, empty orders and stale status changes are all refused with a ready-to-show message.
- `OrderStatus` is an enum (PENDING, PREPARING, READY, SERVED, PAID, CANCELLED) with `next()` / `previous()`; `closedSqlList()` gives the single definition of a closed order used by the SQL.
- Only the Tables screen creates an order (when seating a customer). The Orders screen adds food to it.

## Phase 4 - Billing and payment (added)

`BillingPanel` -> `BillingService` -> `BillDao` -> `BillDaoImpl` (items are read through the existing `OrderDao.findLines`).
- `BillCalculation` (model, no Swing, no SQL) is the ONE money formula: `BigDecimal`, 2 decimals, `HALF_UP`. The screen, the service and the database layer all use it, so the number the cashier sees is the number that is saved.
- `BillingService` validates typed text (discount, GST %), builds the receipt text and refuses payment of anything but a SERVED order.
- `BillDaoImpl.payOrder` is one JDBC transaction: lock order -> checks -> re-calculate -> insert bill -> order PAID -> table AVAILABLE (only if no other open order uses it) -> commit; any error -> rollback. This is the only code that frees a table after billing.
- `ReceiptDialog` shows the receipt in a monospaced `JTextArea`; the Print button uses `JTextArea.print()` (Java's own printing, no extra library).

## Phase 5 - Order History and Reports (added)

Same layers: `HistoryPanel` -> `HistoryService` -> `HistoryDao` -> `HistoryDaoImpl`, and `ReportsPanel` -> `ReportsService` -> `ReportsDao` -> `ReportsDaoImpl`.
- Both are read-only: the DAO interfaces have only SELECT methods, so these screens cannot change an order or a bill.
- History = one SELECT with JOINs (`orders`, `restaurant_tables`, `LEFT JOIN bills`); each filter adds a `AND ... = ?` condition and a PreparedStatement parameter.
- Reports = SQL aggregation: `COUNT`, `SUM`, `AVG`, `GROUP BY`, `ORDER BY`, `LIMIT`. Money counts only bills with payment_status PAID; one bill per order is guaranteed by a UNIQUE key, so nothing is counted twice.
- Java records (`SalesRow`, `TopItem`, `ReportSummary`, ...) carry the finished numbers to the screen; `ReportsService.resolve` holds the date rules (Today, Last 7 days, This month, All time, Custom).
- `BarChartPanel` draws the charts with plain Java 2D, so no chart library is needed.

## Phase 6 - Clear Order History (added)

Same layers: `HistoryPanel` -> `HistoryService` -> `HistoryDao` -> `HistoryDaoImpl`. New DAO methods: `isClearingAvailable()` (reads
`information_schema.columns`), `countClearableOrders()`, `clearClosedOrders()`; `search(filter, limit)` now hides rows with
`history_cleared_at IS NOT NULL` unless `HistoryFilter.includeCleared` is true.
- Clearing is LOGICAL: `UPDATE orders SET history_cleared_at = NOW() WHERE status IN ('PAID','CANCELLED') AND history_cleared_at IS NULL`, inside one transaction (commit, or rollback + error). No DELETE exists anywhere in this feature.
- `bills`, `order_items`, Reports and Dashboard SQL never mention the column, so money figures cannot change.
- Migration `database/migrations/003_add_history_cleared_flag.sql` adds the nullable column. Before it is run, History/Reports/Billing work as before and the button explains the migration; `check-db` reports the missing column.

## v1.0 Final - shared history rule and interface helpers
- `dao/HistoryVisibility` is the single place that decides which orders are hidden from history-style lists (`history_cleared_at IS NULL`). `HistoryDaoImpl` and `DashboardDaoImpl` use it; money queries (Reports, Billing, revenue) never do.
- `ui/components/DataTableStyle` adds sorting (`TableRowSorter`), search (`RowFilter`), empty-state labels and model/view row helpers. `SearchField`, `StatCard.setOnClick`, `SidebarButton` icons, `Background` busy counter and the `MainFrame` loading bar and Alt+1..7 key bindings are UI-only; no business rule lives in them.

## Reset Revenue
`RevenueBaseline` (dao) is the single revenue rule shared by `ReportsDaoImpl` and `DashboardDaoImpl`; `ReportsService` exposes `latestReset/previewReset/resetRevenue`; `RevenueResetAction` (ui) is the shared confirm-and-reset flow used by `ReportsPanel` and `DashboardPanel`. Table `revenue_resets` (migration 004) stores each reset; bills are never changed.

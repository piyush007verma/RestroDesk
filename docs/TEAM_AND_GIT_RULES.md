# Team Work Division and Git Rules

## Who owns what

Each member owns their own files, so merge conflicts are rare.

| Member | Screens | Models / Services / DAOs they create |
|---|---|---|
| 1 - Menu and Tables | `MenuPanel`, `TablesPanel` | `MenuItem`, `MenuService`, `MenuDao(+Impl)`, `TableOverview`, `TableService`, `TableDao(+Impl)`, table assign/release (all built in Milestone 2) |
| 2 - Orders | `OrdersPanel` | `Order`, `OrderItem`, `OrderService`, `OrderDao(+Impl)`, order status |
| 3 - Billing and Database | `BillingPanel` | `Bill`, `BillingService` (GST, discount), `BillDao(+Impl)`, transactions, `DatabaseConnection`, SQL scripts, file export of bills |
| 4 - Dashboard and Integration | `DashboardPanel`, `HistoryPanel`, `ReportsPanel` | `DashboardService`, `ReportService`, history DAO, integration, testing, shared UI (`Theme`, `components`) |

Shared files with ONE owner (ask the owner before changing):
- `Theme.java`, `ui/components/*`, `MainFrame.java`, `Screen.java` -> Member 4
- `schema.sql`, `DatabaseConnection.java` -> Member 3 (schema changes are announced to everyone)

## How the modules integrate without conflicts

- `MainFrame.registerScreens()` has ONE line per screen. A member replaces only their line (for example `new MenuPanel()` becomes `new MenuPanel(new MenuService(new MenuDaoImpl()))`).
- Members use only the **interfaces** of other members' work (for example Billing calls `OrderDao`, never raw SQL on the orders table of someone else).
- Cross-member needs are agreed as an interface first (method names and parameters), then implemented separately.

## Development sequence

1. Milestone 1 (done): scaffold, theme, sidebar, dashboard.
2. Milestone 2 (done): Menu CRUD and Table management (Member 1), custom exceptions, `Background` and `Messages` helpers.
3. Milestone 3: Orders (Member 2) - needs menu and tables.
4. Milestone 4: Billing, GST, discount, payment, table release in one transaction (Member 3).
5. Milestone 5: Order history and reports (Member 4), plus file export.
6. Milestone 6: Integration, validation, error handling, polish, testing, final docs.

## Coding rules

- Java 21. Packages lowercase; classes `PascalCase`; methods and fields `camelCase`; constants `UPPER_SNAKE`.
- SQL only inside `*DaoImpl`, always with `PreparedStatement` (never string-concatenate user input).
- No business logic inside Swing panels; no Swing code inside services or DAOs.
- Colours and fonts only via `Theme`. Reuse `components/*`.
- Never freeze the UI: database work in a `SwingWorker`.
- Every public class gets a short comment explaining what it is for.
- Catch `SQLException` in DAOs and rethrow as `DatabaseException`; panels show a friendly message dialog.
- Never commit passwords (`config/db.properties` is git-ignored).

## Git rules

1. `main` always compiles and runs. Nobody commits directly to `main`.
2. One branch per feature: `feature/menu-crud`, `feature/orders`, `feature/billing`, `feature/reports`.
3. Commit small and often. Message style: `Add menu item form validation`.
4. Before starting work: `git checkout main`, `git pull`, then `git checkout -b feature/...`.
5. When done: push the branch and open a Pull Request; one teammate reviews it; then merge.
6. Before opening a PR run `./build.sh` (or `build.bat`) - it must say "Build OK".
7. If you need to change a shared file, tell the owner in the group chat first.

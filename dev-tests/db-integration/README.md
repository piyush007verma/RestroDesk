# db-integration (optional developer tests, not part of the app)

These two programs test the REAL services and DAOs through the REAL MySQL Connector/J driver against a REAL database.

* `SqlRun.java` - runs a `.sql` file through JDBC (`java -cp "lib/*:tools" SqlRun "<jdbc url without database>" file.sql`).
* `ItTest.java` - phase `A`: menu CRUD, table assign / duplicate assign / release, dashboard (22 checks);
  phase `B` (run in a NEW JVM = "application restarted"): data from phase A still there (4 checks);
  phase `C` (after `sample_data.sql`): dashboard totals, a dish used in orders cannot be deleted, releasing a table (7 checks).

They need an EMPTY `restodesk` database, a `config/db.properties` in the folder you run from, and the driver jar on the classpath.
Run order: create empty `restodesk` -> `SqlRun schema.sql` (twice) -> `ItTest A` -> `ItTest B` -> `SqlRun sample_data.sql` (twice) -> `ItTest C`.
WARNING: they add and delete rows. Never run them against a database whose data you want to keep.

How they were run in Milestone 2.2: the sandbox had no MySQL, so a MySQL-compatible server (Dolt 2.7.0 `dolt sql-server`, port 3307,
reports MySQL 8.0.33) was used with the user's own Connector/J jar. That is NOT real MySQL (see HANDOFF.md section 6).

## Phase 3: OrderItTest
`OrderItTest.java` phase `A` (40 checks) then phase `B` in a NEW JVM (6 checks) on a fresh database (`schema.sql`, no sample data).
Run it BEFORE `P3Test` (P3Test adds a second order). It adds and deletes rows - never run it on data you want to keep.

## Phase 4: BillItTest
`BillItTest.java` on an EMPTY database made with `schema.sql`: `U` (20 checks, no database needed), `A` (46), then `B` in a NEW JVM (7).
Phase A also proves the rollback: it temporarily renames `restaurant_tables.status`, so the last step of the payment fails, checks that nothing was saved, and renames the column back. Never run it on data you want to keep.

## Phase 5: HistReportItTest
`HistReportItTest.java` phase `A` (60 checks) on a fresh database (schema.sql only), then phase `B` in a NEW JVM (4 checks). It backdates two orders with plain SQL
to test date ranges and monthly rows. Run `P5Test` after phase A. It adds rows - never run it on data you want to keep.

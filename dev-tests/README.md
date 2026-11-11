# dev-tests (optional, not part of the app)

`M2Test.java` drives the REAL Menu and Tables screens and the REAL services, but with in-memory fake DAOs,
so it needs NO MySQL. It checks 59 things (validation, add/edit/delete, assign, duplicate assignment, release).
It does NOT test any SQL - that needs a real MySQL.

Run (after `./build.sh`):

    mkdir -p out-tests shots
    javac -cp out -d out-tests dev-tests/M2Test.java
    java -cp "out:out-tests" M2Test shots            # Windows: use ; instead of :

It opens windows for a few seconds and prints PASS/FAIL lines, ending with `RESULT: 59 passed, 0 failed`.
On a server without a screen use `xvfb-run -a java ...`. It also writes two screenshots to `shots/`.

## Phase 3 additions
* `P3Test.java` - drives the REAL `MainFrame` (Orders, Menu, Tables, Dashboard) with the REAL DAOs, so it needs a database
  (a `config/db.properties` in the folder you run from, a database created with `schema.sql`, and tables 6 free). 22 checks.
  `xvfb-run -a java -cp "lib/*:out:out-tests" P3Test shots` (Windows: `;`).
* `db-integration/OrderItTest.java` - phase A (40 checks) and B (6 checks, new JVM = restart) for orders. See `db-integration/README.md`.
* `M2Test.java` still passes 59 checks (its combo index was changed to 1 because the Menu screen now also has a category filter).

## Phase 4 additions
* `P4Test.java` - the REAL `MainFrame` + Billing screen with real DAOs; needs an EMPTY database made with `schema.sql` (it seats tables 3, 4, 5), a `config/db.properties` in the folder you run from, and an existing screenshot folder. 31 checks. It answers the confirmation dialogs itself (No first, then Yes).
  `xvfb-run -a java -cp "lib/*:out:out-tests" P4Test shots` (Windows: `;`).
* `db-integration/BillItTest.java` - phases `U` (money formulas, no database), `A` (billing/payment, 46 checks), `B` (new JVM = restart, 7 checks).
* Run `M2Test` from the project folder (not from a folder whose `db.properties` points to a live database).

## Phase 4 and 5 additions
* `P4Test.java` - real MainFrame, Billing screen, real DAOs. Needs a database made with `schema.sql` that has NO bills yet (31 checks).
* `P5Test.java` - real MainFrame, Order History and Reports. Run it right after `db-integration/HistReportItTest A` on the same database (28 checks):
  `xvfb-run -a java -cp "lib/*:out:out-tests" P5Test shots`.
* `P6Test.java` - real MainFrame, Clear Order History (Cancel, confirm, show cleared; 11 checks). See `db-integration/README_PHASE6.md`.
* Run order on a FRESH database each time: OrderItTest A,B -> P3Test | BillItTest U,A,B | P4Test | HistReportItTest A,B -> P5Test.
* `P7Test.java` - 14-step global clearing test with the real screens: `P7Test A <folder>` on an empty schema-only database, then `P7Test B <folder>` in a NEW JVM (20 + 5 checks).
* `P8Test.java` - sorting, search, shortcuts, empty states, loading bar (14 checks); run after P7Test A.
* `ScreenShots.java` - saves a PNG of every screen: `xvfb-run -a java -cp "lib/*:out:out-tests" ScreenShots <folder>`.
* `P9Test.java` - Reset Revenue with the real screens: `P9Test U x` (database without migration 004, run `DROP TABLE revenue_resets;` on the TEST database first), then on a fresh schema-only database `P9Test A <folder>` and `P9Test B <folder>` in a NEW JVM (6 + 24 + 4 checks).

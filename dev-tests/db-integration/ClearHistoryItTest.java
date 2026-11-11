import com.restaurant.dao.*;
import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import com.restaurant.service.ReportsService.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

/**
 * Phase 6 test: "Clear Order History" with the REAL services and DAOs against a REAL database.
 * Phase U  (database WITHOUT migration 003): history still works, clearing explains what to do.
 * Phase A  (database WITH migration 003): builds paid / cancelled / open orders, takes a snapshot of every money figure,
 *          clears, and proves that nothing financial changed (rows, sums, receipts, every report number).
 * Phase B  (new JVM = application restarted): the cleared state is still there and the money is still the same.
 * Run on an EMPTY database made with schema.sql. Never run it on data you want to keep.
 */
public class ClearHistoryItTest {
    static int pass, fail;
    static void check(String n, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + n); }
    static boolean same(BigDecimal a, String b) { return a != null && a.compareTo(new BigDecimal(b)) == 0; }

    static BillingService billing = new BillingService(new BillDaoImpl(), new OrderDaoImpl());
    static OrderService orders = new OrderService(new OrderDaoImpl());
    static TableService tables = new TableService(new TableDaoImpl());
    static MenuService menu = new MenuService(new MenuDaoImpl());
    static HistoryService history = new HistoryService(new HistoryDaoImpl(), billing, new OrderDaoImpl());
    static ReportsService reports = new ReportsService(new ReportsDaoImpl());

    static TableOverview tbl(int n) throws Exception { return tables.getTables().stream().filter(t -> t.getNumber() == n).findFirst().orElseThrow(); }
    static OrderSummary active(int id) throws Exception { return orders.getActiveOrders().stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null); }
    static com.restaurant.model.MenuItem dish(String name) throws Exception { return menu.getAllItems().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow(); }
    static String text(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); String v = r.getString(1); return v == null ? "" : v; } }
    static long count(String sql) throws Exception { return Long.parseLong(text(sql)); }
    static void exec(String sql) throws Exception { try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement()) { s.execute(sql); } }

    static int openOrder(int table, String customer) throws Exception {
        int id = tables.assignTable(tbl(table).getId(), customer, null);
        orders.addItem(id, dish("Butter Chicken"), 2);
        orders.addItem(id, dish("Masala Chai"), 1);
        return id;
    }
    static int servedOrder(int table, String customer) throws Exception {
        int id = openOrder(table, customer);
        for (OrderStatus s : new OrderStatus[]{OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED}) orders.changeStatus(active(id), s);
        return id;
    }
    static void pay(int id, DiscountType t, String disc, PaymentMethod m) throws Exception {
        OrderSummary o = billing.getOrdersReadyForBilling().stream().filter(x -> x.getOrderId() == id).findFirst().orElseThrow();
        billing.pay(o, billing.calculate(billing.getLines(id), t, disc, "5"), m);
    }
    static List<Integer> ids(HistoryFilter f) throws Exception { return history.search(f).stream().map(HistoryRow::orderId).toList(); }
    static String billFingerprint() throws Exception {
        return text("SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', bill_id, order_id, subtotal, gst_percent, gst_amount, discount_type, discount_value, discount_amount, total_amount, payment_method, payment_status, paid_at) ORDER BY bill_id SEPARATOR ';'), '') FROM bills");
    }
    static String itemFingerprint() throws Exception {
        return text("SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', order_item_id, order_id, item_id, quantity, unit_price) ORDER BY order_item_id SEPARATOR ';'), '') FROM order_items");
    }
    static String orderFingerprint() throws Exception { // everything about an order EXCEPT the new flag column
        return text("SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', order_id, table_id, customer_name, status, created_at) ORDER BY order_id SEPARATOR ';'), '') FROM orders");
    }
    static String reportFingerprint(ReportData d) {
        ReportSummary s = d.summary();
        return s.pendingPayments() + "/" + s.completedPayments() + "/" + s.revenue().toPlainString() + "/"
                + s.averageOrderValue().toPlainString() + "/" + s.discounts().toPlainString() + "/" + s.gst().toPlainString()
                + " D" + d.daily().toString() + " M" + d.monthly().toString() + " T" + d.topItems().toString() + " P" + d.methods().toString();
    }
    static boolean threw(Thr t) { try { t.run(); return false; } catch (Exception e) { return true; } }
    interface Thr { void run() throws Exception; }

    public static void main(String[] a) throws Exception {
        switch (a[0]) { case "U" -> phaseU(); case "A" -> phaseA(); default -> phaseB(); }
        System.out.println("RESULT " + a[0] + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Database WITHOUT the history_cleared_at column (like a database made before Phase 6). */
    static void phaseU() throws Exception {
        check("U: the column does not exist yet (database before migration 003)", count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'orders' AND column_name = 'history_cleared_at'") == 0);
        check("U: clearing is reported as not available", !history.isClearingAvailable());
        int id = servedOrder(1, "Old Style");
        pay(id, DiscountType.NONE, "", PaymentMethod.CASH);
        check("U: Order History still lists orders and none is marked cleared", history.search(HistoryFilter.none()).size() == 1 && !history.search(HistoryFilter.none()).get(0).cleared());
        check("U: including cleared orders also works before the migration", history.search(HistoryFilter.none().withIncludeCleared(true)).size() == 1);
        long before = count("SELECT COUNT(*) FROM orders") + count("SELECT COUNT(*) FROM bills");
        DatabaseException e1 = null, e2 = null;
        try { history.countClearableOrders(); } catch (DatabaseException e) { e1 = e; }
        try { history.clearHistory(); } catch (DatabaseException e) { e2 = e; }
        check("U: counting refuses with a message that names migration 003", e1 != null && e1.getMessage().contains("003_add_history_cleared_flag.sql"));
        check("U: clearing refuses with the same message and changes nothing", e2 != null && e2.getMessage().contains("003_add_history_cleared_flag.sql")
                && count("SELECT COUNT(*) FROM orders") + count("SELECT COUNT(*) FROM bills") == before && history.search(HistoryFilter.none()).size() == 1);
        check("U: Reports and Billing keep working before the migration", reports.load(reports.resolve(Period.ALL_TIME, null, null, LocalDate.now())).summary().completedPayments() == 1
                && billing.getRecentBills().size() == 1);
    }

    static void phaseA() throws Exception {
        LocalDate today = LocalDate.now();
        check("A: the column exists (migration 003 / schema.sql)", history.isClearingAvailable());
        check("A: new orders start NOT cleared", count("SELECT COUNT(*) FROM orders WHERE history_cleared_at IS NOT NULL") == 0);

        int o1 = servedOrder(1, "Asha");   pay(o1, DiscountType.PERCENT, "10", PaymentMethod.UPI);   // 765.45
        int o2 = servedOrder(2, "Bela");   pay(o2, DiscountType.FIXED, "100", PaymentMethod.CASH);   // 745.50
        int o3 = servedOrder(3, "Chet");   pay(o3, DiscountType.NONE, "", PaymentMethod.CARD);       // 850.50
        int o4 = openOrder(4, "Dev");                                                              // PENDING
        int o5 = servedOrder(5, "Esha");                                                           // SERVED, not paid
        int o6 = openOrder(6, "Farid");  tables.releaseTable(tbl(6).getId());                        // CANCELLED
        exec("UPDATE orders SET created_at = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE order_id = " + o1);
        exec("UPDATE bills SET paid_at = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE order_id = " + o1);
        exec("UPDATE orders SET created_at = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE order_id = " + o2);
        exec("UPDATE bills SET paid_at = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE order_id = " + o2);

        // ---- snapshot BEFORE ----
        DateRange all = reports.resolve(Period.ALL_TIME, null, null, today);
        String reportBefore = reportFingerprint(reports.load(all));
        String billsBefore = billFingerprint(), itemsBefore = itemFingerprint(), ordersBefore = orderFingerprint();
        long nOrders = count("SELECT COUNT(*) FROM orders"), nItems = count("SELECT COUNT(*) FROM order_items"), nBills = count("SELECT COUNT(*) FROM bills");
        String sums = text("SELECT CONCAT(SUM(total_amount), '/', SUM(gst_amount), '/', SUM(discount_amount)) FROM bills");
        List<Bill> recentBefore = billing.getRecentBills();
        String receipt1Before = billing.buildReceipt(billing.getBill(recentBefore.stream().filter(b -> b.getOrderId() == o1).findFirst().orElseThrow().getBillId()));
        Map<LocalDate, ReportSummary> periodsBefore = new LinkedHashMap<>();
        for (Period p : new Period[]{Period.TODAY, Period.LAST_7_DAYS, Period.THIS_MONTH, Period.ALL_TIME}) periodsBefore.put(LocalDate.ofEpochDay(p.ordinal()), reports.load(reports.resolve(p, null, null, today)).summary());
        check("A: snapshot taken: 6 orders, 3 bills, revenue 2361.45", nOrders == 6 && nBills == 3 && same(reports.load(all).summary().revenue(), "2361.45"));
        check("A: before clearing, History lists all 6 orders", ids(HistoryFilter.none()).size() == 6);

        int reportOrdersBefore = reports.load(all).summary().totalOrders();
        // ---- what a clear would hide ----
        check("A: 4 orders can be cleared (3 paid + 1 cancelled); open and served orders are not counted", history.countClearableOrders() == 4);
        check("A: counting changes nothing (this is what the dialog asks before 'Cancel')", ids(HistoryFilter.none()).size() == 6 && count("SELECT COUNT(*) FROM orders WHERE history_cleared_at IS NOT NULL") == 0);

        // ---- the clear ----
        int hidden = history.clearHistory();
        check("A: clearing reports 4 orders hidden", hidden == 4);
        List<HistoryRow> shown = history.search(HistoryFilter.none());
        check("A: History now lists only the 2 open orders (pending + served)", shown.size() == 2 && new HashSet<>(ids(HistoryFilter.none())).equals(Set.of(o4, o5)));
        check("A: paid orders are no longer listed (search by id, status PAID and method UPI find nothing)", ids(new HistoryFilter(o1, null, null, null, null, null, null)).isEmpty()
                && ids(new HistoryFilter(null, null, OrderStatus.PAID, null, null, null, null)).isEmpty() && ids(new HistoryFilter(null, null, null, null, PaymentMethod.UPI, null, null)).isEmpty());
        check("A: the cancelled order is hidden too", ids(new HistoryFilter(null, null, OrderStatus.CANCELLED, null, null, null, null)).isEmpty());
        List<HistoryRow> everything = history.search(HistoryFilter.none().withIncludeCleared(true));
        check("A: 'Show cleared orders' lists all 6 again, 4 of them marked cleared", everything.size() == 6 && everything.stream().filter(HistoryRow::cleared).count() == 4
                && everything.stream().filter(HistoryRow::cleared).map(HistoryRow::orderId).collect(java.util.stream.Collectors.toSet()).equals(Set.of(o1, o2, o3, o6)));
        check("A: Reports Total Orders now counts only the 2 open orders (cleared orders are not counted; before the clear it was 6)", reports.load(all).summary().totalOrders() == 2 && reportOrdersBefore == 6);
        check("A: open orders are NOT marked cleared", everything.stream().filter(r -> r.orderId() == o4 || r.orderId() == o5).noneMatch(HistoryRow::cleared));

        // ---- nothing financial changed ----
        System.out.println("== FINANCIAL DATA UNCHANGED ==");
        check("A: no row was deleted (orders 6, order items, bills 3)", count("SELECT COUNT(*) FROM orders") == nOrders && count("SELECT COUNT(*) FROM order_items") == nItems && count("SELECT COUNT(*) FROM bills") == nBills);
        check("A: every bill column is byte-identical (amounts, GST, discount, method, status, paid time)", billFingerprint().equals(billsBefore));
        check("A: every order item (quantity, unit price) is identical", itemFingerprint().equals(itemsBefore));
        check("A: every order (table, customer, status, created time) is identical", orderFingerprint().equals(ordersBefore));
        check("A: sums of total / GST / discount are identical (" + sums + ")", text("SELECT CONCAT(SUM(total_amount), '/', SUM(gst_amount), '/', SUM(discount_amount)) FROM bills").equals(sums));
        check("A: the Reports screen data is identical, every number (summary, daily, monthly, best-selling, payment methods)", reportFingerprint(reports.load(all)).equals(reportBefore));
        boolean periodsSame = true;
        int i = 0;
        for (Period p : new Period[]{Period.TODAY, Period.LAST_7_DAYS, Period.THIS_MONTH, Period.ALL_TIME}) {
            ReportSummary now = reports.load(reports.resolve(p, null, null, today)).summary(), then = periodsBefore.get(LocalDate.ofEpochDay(p.ordinal()));
            periodsSame &= now.revenue().compareTo(then.revenue()) == 0 && now.completedPayments() == then.completedPayments() && now.gst().compareTo(then.gst()) == 0 && now.discounts().compareTo(then.discounts()) == 0 && now.averageOrderValue().compareTo(then.averageOrderValue()) == 0;
        }
        check("A: Today / Last 7 days / This month / All time summaries are identical", periodsSame);
        check("A: revenue is still 2361.45, 3 payments, Cash 745.50 / UPI 765.45 / Card 850.50", same(reports.load(all).summary().revenue(), "2361.45") && reports.load(all).summary().completedPayments() == 3
                && reports.load(all).methods().stream().allMatch(m -> m.payments() == 1));
        check("A: Billing still lists all 3 paid bills and the receipt text is identical", billing.getRecentBills().size() == 3
                && billing.buildReceipt(billing.getBill(billing.getRecentBills().stream().filter(b -> b.getOrderId() == o1).findFirst().orElseThrow().getBillId())).equals(receipt1Before));
        HistoryRow cleared1 = everything.stream().filter(r -> r.orderId() == o1).findFirst().orElseThrow();
        HistoryDetails det = history.getDetails(cleared1);
        check("A: details and receipt of a cleared order still work (765.45, 10% discount, UPI)", det.bill() != null && same(det.bill().getCalculation().getGrandTotal(), "765.45")
                && history.receiptOf(det).contains("765.45") && history.receiptOf(det).contains("UPI"));
        check("A: tables stay as they were (table 1 free, table 4 and 5 still occupied)", tbl(1).getStatus() == TableStatus.AVAILABLE && tbl(4).getStatus() == TableStatus.OCCUPIED && tbl(5).getStatus() == TableStatus.OCCUPIED);
        check("A: open orders still work in Orders after the clear (2 active orders)", orders.getActiveOrders().size() == 2);

        // ---- clearing again / after more business ----
        check("A: clearing again finds nothing and hides nothing", history.countClearableOrders() == 0 && history.clearHistory() == 0 && history.search(HistoryFilter.none()).size() == 2);
        pay(o5, DiscountType.NONE, "", PaymentMethod.UPI);   // 850.50
        check("A: an order paid AFTER the clear appears in History (not hidden) and in Reports", history.search(HistoryFilter.none()).stream().anyMatch(r -> r.orderId() == o5 && r.paymentStatus().equals("PAID"))
                && same(reports.load(all).summary().revenue(), "3211.95") && reports.load(all).summary().completedPayments() == 4);
        check("A: a second clear hides only the newly finished order", history.countClearableOrders() == 1 && history.clearHistory() == 1
                && new HashSet<>(ids(HistoryFilter.none())).equals(Set.of(o4)) && history.search(HistoryFilter.none().withIncludeCleared(true)).size() == 6);
        check("A: revenue after the second clear is still 3211.95", same(reports.load(all).summary().revenue(), "3211.95"));
        check("A: the clear never wrote anything except the flag (no order, bill or item row changed)", count("SELECT COUNT(*) FROM orders") == 6 && count("SELECT COUNT(*) FROM order_items") == nItems && count("SELECT COUNT(*) FROM bills") == 4);
        check("A: only finished orders carry the flag (5 of 6; the pending order does not)", count("SELECT COUNT(*) FROM orders WHERE history_cleared_at IS NOT NULL") == 5
                && count("SELECT COUNT(*) FROM orders WHERE history_cleared_at IS NOT NULL AND status NOT IN ('PAID','CANCELLED')") == 0);
        check("A: filters still combine with 'Show cleared orders' (PAID + UPI = 2 orders)", ids(new HistoryFilter(null, null, OrderStatus.PAID, null, PaymentMethod.UPI, null, null, true)).size() == 2);
    }

    static void phaseB() throws Exception {
        LocalDate today = LocalDate.now();
        check("B (after restart): History shows only the open pending order", new HashSet<>(ids(HistoryFilter.none())).equals(Set.of(4)));
        check("B: 'Show cleared orders' still lists all 6", history.search(HistoryFilter.none().withIncludeCleared(true)).size() == 6);
        check("B: nothing was restored or deleted: 6 orders, 4 bills, 5 cleared flags", count("SELECT COUNT(*) FROM orders") == 6 && count("SELECT COUNT(*) FROM bills") == 4 && count("SELECT COUNT(*) FROM orders WHERE history_cleared_at IS NOT NULL") == 5);
        ReportSummary s = reports.load(reports.resolve(Period.ALL_TIME, null, null, today)).summary();
        check("B: revenue 3211.95, 4 payments, GST 152.95, discounts 181.00 unchanged", same(s.revenue(), "3211.95") && s.completedPayments() == 4 && same(s.discounts(), "181.00") && same(s.gst(), "152.95"));
        check("B: Billing still lists 4 bills and a cleared order's receipt can be shown", billing.getRecentBills().size() == 4
                && billing.buildReceipt(billing.getBill(billing.getRecentBills().get(0).getBillId())).contains("GRAND TOTAL"));
    }
}

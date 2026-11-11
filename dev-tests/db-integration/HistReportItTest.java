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
 * Phase 5 test: Order History + Reports with the REAL services and DAOs against a REAL database.
 * Phase A builds 6 orders of different kinds (paid with UPI / Cash / Card, open, served-unpaid, cancelled), moves their
 * dates into the past with plain SQL, then checks every filter and every report number against hand-calculated values
 * AND against separate raw SQL. Phase B (new JVM = restart) checks that everything is still there.
 * Run on an EMPTY database made with schema.sql (no sample data). Never run it on data you want to keep.
 */
public class HistReportItTest {
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
    static BigDecimal scalar(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); BigDecimal v = r.getBigDecimal(1); return v == null ? BigDecimal.ZERO : v; } }
    static long count(String sql) throws Exception { return scalar(sql).longValue(); }
    static void exec(String sql) throws Exception { try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement()) { s.execute(sql); } }

    /** Butter Chicken 380 x2 + Masala Chai 50 x1 = 810, walked to SERVED. */
    static int servedOrder(int table, String customer) throws Exception {
        int id = openOrder(table, customer);
        for (OrderStatus s : new OrderStatus[]{OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED}) orders.changeStatus(active(id), s);
        return id;
    }
    static int openOrder(int table, String customer) throws Exception {
        int id = tables.assignTable(tbl(table).getId(), customer, null);
        orders.addItem(id, dish("Butter Chicken"), 2);
        orders.addItem(id, dish("Masala Chai"), 1);
        return id;
    }
    static void pay(int id, DiscountType t, String disc, PaymentMethod m) throws Exception {
        OrderSummary o = billing.getOrdersReadyForBilling().stream().filter(x -> x.getOrderId() == id).findFirst().orElseThrow();
        billing.pay(o, billing.calculate(billing.getLines(id), t, disc, "5"), m);
    }
    static HistoryFilter f(Integer id, Integer table, OrderStatus st, String pay, PaymentMethod m, LocalDate from, LocalDate to) { return new HistoryFilter(id, table, st, pay, m, from, to); }
    static List<Integer> ids(HistoryFilter filter) throws Exception { return history.search(filter).stream().map(HistoryRow::orderId).toList(); }
    static boolean refused(Runnable0 r) { try { r.run(); return false; } catch (InvalidInputException e) { return true; } catch (Exception e) { return false; } }
    interface Runnable0 { void run() throws Exception; }

    public static void main(String[] a) throws Exception {
        if (a[0].equals("A")) phaseA(); else phaseB();
        System.out.println("RESULT " + a[0] + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void phaseA() throws Exception {
        LocalDate today = LocalDate.now();
        check("empty database: history is empty and reports are zero", history.search(HistoryFilter.none()).isEmpty()
                && reports.load(reports.resolve(Period.ALL_TIME, null, null, today)).summary().totalOrders() == 0);

        // ---- build the data ----
        int o1 = servedOrder(1, "Asha");   pay(o1, DiscountType.PERCENT, "10", PaymentMethod.UPI);   // 810 -81 = 729 +36.45 = 765.45
        int o2 = servedOrder(2, "Bela");   pay(o2, DiscountType.FIXED, "100", PaymentMethod.CASH);   // 810 -100 = 710 +35.50 = 745.50
        int o3 = servedOrder(3, "Chet");   pay(o3, DiscountType.NONE, "", PaymentMethod.CARD);       // 810 +40.50 = 850.50
        int o4 = openOrder(4, "Dev");                                                              // PENDING, has food
        int o5 = servedOrder(5, "Esha");                                                           // SERVED, not paid
        int o6 = openOrder(6, "Farid");  tables.releaseTable(tbl(6).getId());                        // CANCELLED, had food
        exec("UPDATE orders SET created_at = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE order_id = " + o1);
        exec("UPDATE bills SET paid_at = DATE_SUB(NOW(), INTERVAL 2 DAY) WHERE order_id = " + o1);
        exec("UPDATE orders SET created_at = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE order_id = " + o2);
        exec("UPDATE bills SET paid_at = DATE_SUB(NOW(), INTERVAL 40 DAY) WHERE order_id = " + o2);
        LocalDate d1 = today.minusDays(2), d2 = today.minusDays(40);

        long ordersBefore = count("SELECT COUNT(*) FROM orders"), billsBefore = count("SELECT COUNT(*) FROM bills"), itemsBefore = count("SELECT COUNT(*) FROM order_items");

        // ---- Order History ----
        System.out.println("== ORDER HISTORY ==");
        List<HistoryRow> all = history.search(HistoryFilter.none());
        check("all 6 orders are listed, newest first", all.size() == 6 && all.get(0).orderId() == o6 && all.get(5).orderId() == o1);
        HistoryRow r1 = all.stream().filter(r -> r.orderId() == o1).findFirst().orElseThrow();
        check("paid order shows table 1, customer, PAID, UPI, total 765.45, 3 items", r1.tableNumber() == 1 && r1.customerName().equals("Asha")
                && r1.paymentStatus().equals("PAID") && r1.paymentMethod() == PaymentMethod.UPI && same(r1.total(), "765.45") && r1.itemCount() == 3 && r1.status() == OrderStatus.PAID);
        HistoryRow r5 = all.stream().filter(r -> r.orderId() == o5).findFirst().orElseThrow();
        check("served order is UNPAID with no total and no method", r5.paymentStatus().equals("UNPAID") && r5.total() == null && r5.paymentMethod() == null && !r5.hasBill());
        HistoryRow r6 = all.stream().filter(r -> r.orderId() == o6).findFirst().orElseThrow();
        check("cancelled order shows payment N/A and CANCELLED", r6.paymentStatus().equals("N/A") && r6.status() == OrderStatus.CANCELLED);
        check("search by Order ID", ids(f(o3, null, null, null, null, null, null)).equals(List.of(o3)));
        check("search by Order ID that does not exist = empty", ids(f(9999, null, null, null, null, null, null)).isEmpty());
        check("search by Table Number 2", ids(f(null, 2, null, null, null, null, null)).equals(List.of(o2)));
        check("filter status PAID = 3 orders", ids(f(null, null, OrderStatus.PAID, null, null, null, null)).size() == 3);
        check("filter status PENDING = the open order", ids(f(null, null, OrderStatus.PENDING, null, null, null, null)).equals(List.of(o4)));
        check("filter status SERVED = the unpaid served order", ids(f(null, null, OrderStatus.SERVED, null, null, null, null)).equals(List.of(o5)));
        check("filter status CANCELLED", ids(f(null, null, OrderStatus.CANCELLED, null, null, null, null)).equals(List.of(o6)));
        check("filter payment PAID = 3", ids(f(null, null, null, HistoryFilter.PAID, null, null, null)).size() == 3);
        check("filter payment UNPAID = open + served-unpaid (cancelled is NOT unpaid)", new HashSet<>(ids(f(null, null, null, HistoryFilter.UNPAID, null, null, null))).equals(Set.of(o4, o5)));
        check("filter method UPI", ids(f(null, null, null, null, PaymentMethod.UPI, null, null)).equals(List.of(o1)));
        check("filter method CASH", ids(f(null, null, null, null, PaymentMethod.CASH, null, null)).equals(List.of(o2)));
        check("filter method CARD", ids(f(null, null, null, null, PaymentMethod.CARD, null, null)).equals(List.of(o3)));
        check("two filters together (PAID + CASH)", ids(f(null, null, OrderStatus.PAID, HistoryFilter.PAID, PaymentMethod.CASH, null, null)).equals(List.of(o2)));
        check("conflicting filters (PENDING + UPI) = empty", ids(f(null, null, OrderStatus.PENDING, null, PaymentMethod.UPI, null, null)).isEmpty());
        check("date range last 3 days excludes the 40-day-old order", new HashSet<>(ids(f(null, null, null, null, null, today.minusDays(3), today))).equals(Set.of(o1, o3, o4, o5, o6)));
        check("date range today only", new HashSet<>(ids(f(null, null, null, null, null, today, today))).equals(Set.of(o3, o4, o5, o6)));
        check("date range around the old order only", ids(f(null, null, null, null, null, d2.minusDays(1), d2.plusDays(1))).equals(List.of(o2)));
        check("input checks: text, zero, negative, 12.5 and start-after-end are refused",
                refused(() -> history.buildFilter("abc", "", null, null, null, null, null))
                && refused(() -> history.buildFilter("0", "", null, null, null, null, null))
                && refused(() -> history.buildFilter("", "-3", null, null, null, null, null))
                && refused(() -> history.buildFilter("12.5", "", null, null, null, null, null))
                && refused(() -> history.buildFilter("", "", null, null, null, today, today.minusDays(1))));
        HistoryFilter built = history.buildFilter(" 7 ", "", null, null, null, null, null);
        check("blank means no filter, spaces around a number are accepted", built.orderId() == 7 && built.tableNumber() == null);

        HistoryDetails det = history.getDetails(r1);
        check("details of a paid order: bill, 2 lines, breakdown", det.bill() != null && det.lines().size() == 2
                && same(det.bill().getCalculation().getSubtotal(), "810") && same(det.bill().getCalculation().getDiscountAmount(), "81.00")
                && same(det.bill().getCalculation().getGstAmount(), "36.45") && same(det.bill().getCalculation().getGrandTotal(), "765.45"));
        String receipt = history.receiptOf(det);
        check("receipt can be rebuilt from history", receipt != null && receipt.contains("GRAND TOTAL") && receipt.contains("765.45") && receipt.contains("UPI") && receipt.contains("Discount (10%)"));
        HistoryDetails det5 = history.getDetails(r5);
        check("details of an unbilled order: items shown, no bill, no receipt", det5.bill() == null && det5.lines().size() == 2 && history.receiptOf(det5) == null);
        HistoryDetails det6 = history.getDetails(r6);
        check("details of a cancelled order still show its items", det6.lines().size() == 2 && det6.bill() == null);
        check("history screen cannot change data (row counts unchanged)", count("SELECT COUNT(*) FROM orders") == ordersBefore
                && count("SELECT COUNT(*) FROM bills") == billsBefore && count("SELECT COUNT(*) FROM order_items") == itemsBefore);

        // ---- Reports ----
        System.out.println("== REPORTS ==");
        DateRange allTime = reports.resolve(Period.ALL_TIME, null, null, today);
        ReportData all5 = reports.load(allTime);
        ReportSummary s = all5.summary();
        check("summary: 6 orders, 1 cancelled, 1 pending payment, 3 completed payments", s.totalOrders() == 6 && s.cancelledOrders() == 1 && s.pendingPayments() == 1 && s.completedPayments() == 3);
        check("summary: revenue 2361.45 (765.45 + 745.50 + 850.50), only PAID bills", same(s.revenue(), "2361.45"));
        check("summary: average order value 787.15", same(s.averageOrderValue(), "787.15"));
        check("summary: discounts 181.00, GST 112.45", same(s.discounts(), "181.00") && same(s.gst(), "112.45"));
        check("revenue equals a separate raw SQL SUM", same(s.revenue(), scalar("SELECT SUM(total_amount) FROM bills WHERE payment_status = 'PAID'").toPlainString()));
        check("each paid bill is counted exactly once (bills per order = 1)", count("SELECT COUNT(*) FROM (SELECT order_id FROM bills GROUP BY order_id HAVING COUNT(*) > 1) x") == 0);
        boolean second;
        try { exec("INSERT INTO bills (order_id, subtotal, gst_amount, discount_amount, total_amount, payment_method) VALUES (" + o1 + ", 1, 0, 0, 1, 'CASH')"); second = true; } catch (SQLException e) { second = false; }
        check("the database refuses a second bill for the same order (no double counting possible)", !second);

        // an unpaid/pending bill row must NOT count as revenue
        exec("INSERT INTO bills (order_id, subtotal, gst_percent, gst_amount, discount_amount, total_amount, payment_method, payment_status) VALUES (" + o5 + ", 810, 5, 40.50, 0, 850.50, 'CASH', 'PENDING')");
        ReportSummary s2 = reports.load(allTime).summary();
        check("a bill that is NOT paid (status PENDING) is not counted as revenue or as a completed payment", same(s2.revenue(), "2361.45") && s2.completedPayments() == 3);
        check("...and history shows that order as UNPAID", history.search(f(o5, null, null, null, null, null, null)).get(0).paymentStatus().equals("UNPAID"));
        exec("DELETE FROM bills WHERE order_id = " + o5);

        check("daily sales: 3 days, oldest first", all5.daily().size() == 3 && all5.daily().get(0).day().equals(d2) && all5.daily().get(2).day().equals(today));
        SalesRow day1 = all5.daily().stream().filter(r -> r.day().equals(d1)).findFirst().orElseThrow();
        check("daily row of the UPI order: 1 order, sales 765.45, discount 81.00, GST 36.45", day1.orders() == 1 && same(day1.sales(), "765.45") && same(day1.discounts(), "81.00") && same(day1.gst(), "36.45"));
        BigDecimal dailySum = all5.daily().stream().map(SalesRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add);
        check("daily rows add up to the revenue", dailySum.compareTo(s.revenue()) == 0);
        Set<LocalDate> months = new HashSet<>(List.of(d1.withDayOfMonth(1), d2.withDayOfMonth(1), today.withDayOfMonth(1)));
        check("monthly sales: one row per month with payments (" + months.size() + ")", all5.monthly().size() == months.size());
        check("monthly rows add up to the revenue, discounts and GST", all5.monthly().stream().map(SalesRow::sales).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(s.revenue()) == 0
                && all5.monthly().stream().map(SalesRow::discounts).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(s.discounts()) == 0
                && all5.monthly().stream().map(SalesRow::gst).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(s.gst()) == 0);
        check("monthly rows start on day 1 of the month", all5.monthly().stream().allMatch(r -> r.day().getDayOfMonth() == 1));

        List<TopItem> top = all5.topItems();
        check("best-selling: Butter Chicken first, 6 sold, sales 2280.00 (only PAID orders; open, unpaid and cancelled orders ignored)",
                top.size() == 2 && top.get(0).name().equals("Butter Chicken") && top.get(0).quantity() == 6 && same(top.get(0).sales(), "2280.00"));
        check("best-selling: Masala Chai 3 sold, sales 150.00", top.get(1).name().equals("Masala Chai") && top.get(1).quantity() == 3 && same(top.get(1).sales(), "150.00"));
        List<MethodTotal> m = all5.methods();
        check("payment methods: all three present", m.size() == 3);
        check("Cash 1 payment 745.50", m.stream().anyMatch(x -> x.method() == PaymentMethod.CASH && x.payments() == 1 && same(x.amount(), "745.50")));
        check("UPI 1 payment 765.45", m.stream().anyMatch(x -> x.method() == PaymentMethod.UPI && x.payments() == 1 && same(x.amount(), "765.45")));
        check("Card 1 payment 850.50", m.stream().anyMatch(x -> x.method() == PaymentMethod.CARD && x.payments() == 1 && same(x.amount(), "850.50")));

        // ---- periods ----
        ReportSummary td = reports.load(reports.resolve(Period.TODAY, null, null, today)).summary();
        check("Today: 4 orders created today, 1 payment, revenue 850.50", td.totalOrders() == 4 && td.completedPayments() == 1 && same(td.revenue(), "850.50"));
        ReportSummary w = reports.load(reports.resolve(Period.LAST_7_DAYS, null, null, today)).summary();
        check("Last 7 days: 2 payments, revenue 1615.95", w.completedPayments() == 2 && same(w.revenue(), "1615.95") && same(w.averageOrderValue(), "807.98"));
        BigDecimal monthExpected = BigDecimal.ZERO; int monthCount = 0;
        for (Object[] p : new Object[][]{{d1, "765.45"}, {d2, "745.50"}, {today, "850.50"}}) if (!((LocalDate) p[0]).isBefore(today.withDayOfMonth(1))) { monthExpected = monthExpected.add(new BigDecimal((String) p[1])); monthCount++; }
        ReportSummary mo = reports.load(reports.resolve(Period.THIS_MONTH, null, null, today)).summary();
        check("This month: revenue and payments match the dates", mo.completedPayments() == monthCount && mo.revenue().compareTo(monthExpected) == 0);
        ReportSummary cu = reports.load(reports.resolve(Period.CUSTOM, d2, d2, today)).summary();
        check("Custom range of one old day: the 40-day-old payment only", cu.completedPayments() == 1 && same(cu.revenue(), "745.50") && cu.totalOrders() == 1);
        ReportData none = reports.load(reports.resolve(Period.CUSTOM, LocalDate.of(2001, 1, 1), LocalDate.of(2001, 1, 31), today));
        check("a period with no data: zeros, no rows, three methods with 0", none.summary().totalOrders() == 0 && same(none.summary().revenue(), "0") && same(none.summary().averageOrderValue(), "0")
                && none.daily().isEmpty() && none.monthly().isEmpty() && none.topItems().isEmpty() && none.methods().size() == 3 && none.methods().stream().allMatch(x -> x.payments() == 0));
        LocalDate fixed = LocalDate.of(2026, 11, 7);
        check("period dates: Today / Last 7 days / This month", reports.resolve(Period.TODAY, null, null, fixed).equals(new DateRange(fixed, fixed))
                && reports.resolve(Period.LAST_7_DAYS, null, null, fixed).equals(new DateRange(LocalDate.of(2026, 11, 1), fixed))
                && reports.resolve(Period.THIS_MONTH, null, null, fixed).equals(new DateRange(LocalDate.of(2026, 11, 1), fixed)));
        check("invalid custom ranges are refused with a message", refused(() -> reports.resolve(Period.CUSTOM, today, today.minusDays(1), today))
                && refused(() -> reports.resolve(Period.CUSTOM, null, today, today)) && refused(() -> reports.resolve(Period.CUSTOM, today, null, today))
                && refused(() -> reports.resolve(Period.CUSTOM, LocalDate.of(1990, 1, 1), today, today)));

        // ---- released table does not touch history ----
        check("tables of the paid orders were released but their orders are still in history", tbl(1).getStatus() == TableStatus.AVAILABLE && ids(f(o1, null, null, null, null, null, null)).equals(List.of(o1)));
        int again = tables.assignTable(tbl(1).getId(), "New Guest", null);
        check("a new order on the same table does not replace the old one", ids(f(null, 1, null, null, null, null, null)).size() == 2 && ids(f(o1, null, null, null, null, null, null)).equals(List.of(o1)) && again > o6);
        check("history screens never changed the data (orders 7, bills 3)", count("SELECT COUNT(*) FROM orders") == ordersBefore + 1 && count("SELECT COUNT(*) FROM bills") == billsBefore);
    }

    static void phaseB() throws Exception {
        LocalDate today = LocalDate.now();
        List<HistoryRow> all = history.search(HistoryFilter.none());
        check("after restart: all 7 orders still in history", all.size() == 7);
        check("after restart: 3 PAID orders with the right totals", history.search(f(null, null, null, HistoryFilter.PAID, null, null, null)).stream().map(HistoryRow::total).map(BigDecimal::toPlainString).sorted().toList().equals(List.of("745.50", "765.45", "850.50")));
        ReportSummary s = reports.load(reports.resolve(Period.ALL_TIME, null, null, today)).summary();
        check("after restart: revenue 2361.45, 3 payments, 7 orders", same(s.revenue(), "2361.45") && s.completedPayments() == 3 && s.totalOrders() == 7);
        check("after restart: receipt of a paid order can still be shown", history.receiptOf(history.getDetails(all.stream().filter(r -> r.hasBill()).findFirst().orElseThrow())).contains("GRAND TOTAL"));
    }
}

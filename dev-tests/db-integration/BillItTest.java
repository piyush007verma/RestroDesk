import com.restaurant.dao.*;
import com.restaurant.db.DatabaseConnection;
import com.restaurant.exception.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Phase 4 test with the REAL services and DAOs against a REAL database.
 * Phase U = money formulas only (no database). Phase A = billing and payment. Phase B (new JVM = restart) = history kept.
 * Run on an EMPTY database made with schema.sql (no sample data). It adds rows: never run it on data you want to keep.
 */
public class BillItTest {
    static int pass, fail;
    static void check(String n, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + n); }
    static BigDecimal d(String s) { return new BigDecimal(s); }
    static boolean same(BigDecimal a, String b) { return a.compareTo(new BigDecimal(b)) == 0; }

    static BillingService billing = new BillingService(new BillDaoImpl(), new OrderDaoImpl());
    static OrderService orders = new OrderService(new OrderDaoImpl());
    static TableService tables = new TableService(new TableDaoImpl());
    static MenuService menu = new MenuService(new MenuDaoImpl());

    static TableOverview tbl(int n) throws Exception { return tables.getTables().stream().filter(t -> t.getNumber() == n).findFirst().orElseThrow(); }
    static OrderSummary active(int id) throws Exception { return orders.getActiveOrders().stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null); }
    static OrderSummary ready(int id) throws Exception { return billing.getOrdersReadyForBilling().stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null); }
    static com.restaurant.model.MenuItem dish(String name) throws Exception { return menu.getAllItems().stream().filter(m -> m.getName().equals(name)).findFirst().orElseThrow(); }
    static long count(String sql) throws Exception {
        try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) { r.next(); return r.getLong(1); } }
    static void exec(String sql) throws Exception { try (Connection c = DatabaseConnection.getConnection(); Statement s = c.createStatement()) { s.execute(sql); } }

    /** seats a customer on a table, adds two dishes (Butter Chicken 380 x2, Masala Chai 50 x1 = 810) and walks the order to SERVED. */
    static int servedOrder(int tableNumber, String customer) throws Exception {
        int id = tables.assignTable(tbl(tableNumber).getId(), customer, null);
        orders.addItem(id, dish("Butter Chicken"), 2);
        orders.addItem(id, dish("Masala Chai"), 1);
        for (OrderStatus s : new OrderStatus[]{OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED}) orders.changeStatus(active(id), s);
        return id;
    }
    static boolean refused(Runnable0 r, String... words) {
        try { r.run(); return false; } catch (InvalidInputException e) { String m = e.getMessage().toLowerCase(); for (String w : words) if (!m.contains(w)) return false; return true; } catch (Exception e) { return false; } }
    interface Runnable0 { void run() throws Exception; }

    public static void main(String[] a) throws Exception {
        switch (a[0]) { case "U" -> unit(); case "A" -> phaseA(); default -> phaseB(); }
        System.out.println("RESULT " + a[0] + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------------- money formulas, no database ----------------
    static void unit() throws Exception {
        BillCalculation c = BillCalculation.calculate(d("1000"), DiscountType.PERCENT, d("10"), d("5"));
        check("1000, 10% off, GST 5%: discount 100.00, taxable 900.00, GST 45.00, total 945.00",
                same(c.getDiscountAmount(), "100.00") && same(c.getTaxableAmount(), "900.00") && same(c.getGstAmount(), "45.00") && same(c.getGrandTotal(), "945.00"));
        c = BillCalculation.calculate(d("1000"), DiscountType.FIXED, d("150"), d("5"));
        check("1000, fixed 150 off: taxable 850.00, GST 42.50, total 892.50", same(c.getTaxableAmount(), "850.00") && same(c.getGstAmount(), "42.50") && same(c.getGrandTotal(), "892.50"));
        c = BillCalculation.calculate(d("1000"), DiscountType.NONE, d("999"), d("5"));
        check("no discount ignores the typed value: total 1050.00", same(c.getDiscountAmount(), "0.00") && same(c.getGrandTotal(), "1050.00"));
        c = BillCalculation.calculate(d("99.99"), DiscountType.PERCENT, d("12.5"), d("5"));
        check("rounding HALF_UP: 99.99 less 12.5% = discount 12.50, taxable 87.49, GST 4.37, total 91.86",
                same(c.getDiscountAmount(), "12.50") && same(c.getTaxableAmount(), "87.49") && same(c.getGstAmount(), "4.37") && same(c.getGrandTotal(), "91.86"));
        c = BillCalculation.calculate(d("0.10").add(d("0.20")), DiscountType.NONE, null, d("0"));
        check("BigDecimal: 0.10 + 0.20 is exactly 0.30 (no floating point error), GST 0%", same(c.getGrandTotal(), "0.30"));
        c = BillCalculation.calculate(d("500"), DiscountType.PERCENT, d("100"), d("18"));
        check("100% discount: total 0.00, never negative", same(c.getGrandTotal(), "0.00"));
        c = BillCalculation.calculate(d("500"), DiscountType.FIXED, d("500"), d("5"));
        check("fixed discount equal to the subtotal is allowed: total 0.00", same(c.getGrandTotal(), "0.00"));
        check("negative discount refused", refused(() -> BillCalculation.calculate(d("500"), DiscountType.FIXED, d("-1"), d("5")), "negative"));
        check("percentage above 100 refused", refused(() -> BillCalculation.calculate(d("500"), DiscountType.PERCENT, d("100.01"), d("5")), "between 0 and 100"));
        check("fixed discount above the subtotal refused", refused(() -> BillCalculation.calculate(d("500"), DiscountType.FIXED, d("500.01"), d("5")), "more than the subtotal"));
        check("GST above 100 refused", refused(() -> BillCalculation.calculate(d("500"), DiscountType.NONE, null, d("101")), "gst"));
        check("negative GST refused", refused(() -> BillCalculation.calculate(d("500"), DiscountType.NONE, null, d("-1")), "gst"));
        check("empty bill (subtotal 0) refused", refused(() -> BillCalculation.calculate(BigDecimal.ZERO, DiscountType.NONE, null, d("5")), "nothing to bill"));

        List<OrderLine> lines = List.of(new OrderLine(1, "A", "X", d("380.00"), 2), new OrderLine(2, "B", "X", d("50.00"), 1));
        check("service: subtotal of lines = 810.00", same(billing.subtotalOf(lines), "810.00"));
        BillCalculation viaText = billing.calculate(lines, DiscountType.PERCENT, "10", "5");
        check("service parses typed text: 810 less 10% -> total 765.45", same(viaText.getGrandTotal(), "765.45"));
        check("empty GST text uses the default GST (5%)", same(billing.calculate(lines, DiscountType.NONE, "", "").getGstPercent(), "5.00"));
        check("service: 'abc' refused with a clear message", refused(() -> billing.calculate(lines, DiscountType.FIXED, "abc", "5"), "number"));
        check("service: '-5' refused", refused(() -> billing.calculate(lines, DiscountType.FIXED, "-5", "5"), "negative"));
        check("service: '10.999' refused (max 2 decimals)", refused(() -> billing.calculate(lines, DiscountType.FIXED, "10.999", "5"), "number"));
        check("service: 1e3 refused", refused(() -> billing.calculate(lines, DiscountType.FIXED, "1e3", "5"), "number"));
    }

    // ---------------- billing and payment against the database ----------------
    static void phaseA() throws Exception {
        check("start: no bills", count("SELECT COUNT(*) FROM bills") == 0);

        // an order that is not served yet cannot be billed and does not even show in the list
        int early = tables.assignTable(tbl(1).getId(), "Early Bird", null);
        orders.addItem(early, dish("Butter Chicken"), 1);
        check("PENDING order is not in the billing list", ready(early) == null);
        OrderSummary pendingSummary = active(early);
        BillCalculation anyCalc = BillCalculation.calculate(d("380"), DiscountType.NONE, null, d("5"));
        check("PENDING order cannot be paid (service)", refused(() -> billing.pay(pendingSummary, anyCalc, PaymentMethod.CASH), "served"));
        check("PENDING order cannot be paid (database check, stale screen)", refused(() -> new BillDaoImpl().payOrder(early, anyCalc, PaymentMethod.CASH), "served"));
        orders.changeStatus(active(early), OrderStatus.PREPARING);
        check("PREPARING order is not in the billing list", ready(early) == null);
        orders.changeStatus(active(early), OrderStatus.READY);
        check("READY order is not in the billing list", ready(early) == null);
        check("table 1 still OCCUPIED, no bill", tbl(1).getStatus() == TableStatus.OCCUPIED && count("SELECT COUNT(*) FROM bills") == 0);

        // ---- Cash with percentage discount
        int o3 = servedOrder(3, "Anita Rao");
        OrderSummary s3 = ready(o3);
        check("SERVED order is in the billing list with table 3, SERVED, 3 items, total 810.00", s3 != null && s3.getTableNumber() == 3 && s3.getStatus() == OrderStatus.SERVED && s3.getItemCount() == 3 && same(s3.getTotal(), "810.00"));
        check("billing list holds exactly the served orders (only this one so far)", billing.getOrdersReadyForBilling().size() == 1);
        List<OrderLine> lines3 = billing.getLines(o3);
        check("items of the order are fetched (2 lines, subtotal 810.00)", lines3.size() == 2 && same(billing.subtotalOf(lines3), "810.00"));
        BillCalculation calc3 = billing.calculate(lines3, DiscountType.PERCENT, "10", "5");
        check("bill: subtotal 810.00, discount 81.00, taxable 729.00, GST 36.45, total 765.45",
                same(calc3.getSubtotal(), "810.00") && same(calc3.getDiscountAmount(), "81.00") && same(calc3.getTaxableAmount(), "729.00") && same(calc3.getGstAmount(), "36.45") && same(calc3.getGrandTotal(), "765.45"));
        check("BEFORE payment: table 3 OCCUPIED, order SERVED, no bill (generating a bill releases nothing)",
                tbl(3).getStatus() == TableStatus.OCCUPIED && active(o3).getStatus() == OrderStatus.SERVED && count("SELECT COUNT(*) FROM bills WHERE order_id = " + o3) == 0);
        Bill b3 = billing.pay(s3, calc3, PaymentMethod.CASH);
        check("CASH payment saved: bill id, order id, method CASH, status PAID, paid time present",
                b3.getBillId() > 0 && b3.getOrderId() == o3 && b3.getPaymentMethod() == PaymentMethod.CASH && b3.getPaymentStatus().equals("PAID") && b3.getPaidAt() != null);
        check("saved bill: subtotal 810.00, discount 81.00 (10 PERCENT), GST 5% = 36.45, total 765.45",
                same(b3.getCalculation().getSubtotal(), "810.00") && same(b3.getCalculation().getDiscountAmount(), "81.00") && b3.getCalculation().getDiscountType() == DiscountType.PERCENT
                        && same(b3.getCalculation().getDiscountValue(), "10") && same(b3.getCalculation().getGstAmount(), "36.45") && same(b3.getCalculation().getGrandTotal(), "765.45"));
        check("AFTER payment: table 3 AVAILABLE, no customer", tbl(3).getStatus() == TableStatus.AVAILABLE && tbl(3).getCustomerName() == null);
        check("AFTER payment: order is PAID in the database and gone from Active Orders and the billing list",
                count("SELECT COUNT(*) FROM orders WHERE order_id = " + o3 + " AND status = 'PAID'") == 1 && active(o3) == null && ready(o3) == null);
        check("bill keeps its items (2 lines)", b3.getLines().size() == 2);
        String receipt = billing.buildReceipt(b3);
        check("receipt shows name, bill/order/table, items, quantity, price, discount, GST, grand total, method, status",
                receipt.contains("RestoDesk") && receipt.contains("Bill No   : " + b3.getBillId()) && receipt.contains("Order No  : " + o3) && receipt.contains("Table     : 3")
                        && receipt.contains("Butter Chicken") && receipt.contains("₹380.00") && receipt.contains("₹760.00") && receipt.contains("Discount (10%)")
                        && receipt.contains("GST (5%)") && receipt.contains("₹765.45") && receipt.contains("Cash") && receipt.contains("PAID"));
        System.out.println(receipt);

        // ---- duplicate payment
        long billsBefore = count("SELECT COUNT(*) FROM bills");
        check("duplicate payment of the same order refused (already paid)", refused(() -> billing.pay(s3, calc3, PaymentMethod.UPI), "already been paid"));
        check("duplicate payment recorded nothing", count("SELECT COUNT(*) FROM bills") == billsBefore);
        // stale screen: the order looks SERVED to the service but the database says PAID
        check("two cashiers: second payment attempt refused by the database", refused(() -> new BillDaoImpl().payOrder(o3, calc3, PaymentMethod.CARD), "already"));

        // ---- UPI with fixed discount
        int o4 = servedOrder(4, "Vikram Shah");
        BillCalculation calc4 = billing.calculate(billing.getLines(o4), DiscountType.FIXED, "100", "5");
        check("fixed discount 100 on 810: taxable 710.00, GST 35.50, total 745.50", same(calc4.getTaxableAmount(), "710.00") && same(calc4.getGstAmount(), "35.50") && same(calc4.getGrandTotal(), "745.50"));
        Bill b4 = billing.pay(ready(o4), calc4, PaymentMethod.UPI);
        check("UPI payment saved (FIXED 100.00), table 4 AVAILABLE", b4.getPaymentMethod() == PaymentMethod.UPI && b4.getCalculation().getDiscountType() == DiscountType.FIXED && same(b4.getCalculation().getDiscountValue(), "100") && tbl(4).getStatus() == TableStatus.AVAILABLE);
        check("fixed discount shown on receipt", billing.buildReceipt(b4).contains("Discount (fixed)"));

        // ---- Card, no discount, other GST
        int o5 = servedOrder(5, "Neha Gupta");
        BillCalculation calc5 = billing.calculate(billing.getLines(o5), DiscountType.NONE, "", "18");
        Bill b5 = billing.pay(ready(o5), calc5, PaymentMethod.CARD);
        check("CARD payment, no discount, GST 18%: GST 145.80, total 955.80", b5.getPaymentMethod() == PaymentMethod.CARD && same(b5.getCalculation().getGstAmount(), "145.80") && same(b5.getCalculation().getGrandTotal(), "955.80") && b5.getCalculation().getDiscountType() == DiscountType.NONE);

        // ---- validation: invalid input never reaches the database
        int o6 = servedOrder(6, "Ravi Kumar");
        OrderSummary s6 = ready(o6);
        List<OrderLine> lines6 = billing.getLines(o6);
        check("discount 'abc' refused", refused(() -> billing.calculate(lines6, DiscountType.PERCENT, "abc", "5"), "number"));
        check("percentage 150 refused", refused(() -> billing.calculate(lines6, DiscountType.PERCENT, "150", "5"), "between 0 and 100"));
        check("fixed discount 5000 on an 810 bill refused", refused(() -> billing.calculate(lines6, DiscountType.FIXED, "5000", "5"), "subtotal"));
        BillCalculation calc6 = billing.calculate(lines6, DiscountType.NONE, "", "5");
        check("no payment method refused", refused(() -> billing.pay(s6, calc6, null), "payment method"));
        check("no bill refused", refused(() -> billing.pay(s6, null, PaymentMethod.CASH), "calculated"));
        check("no order refused", refused(() -> billing.pay(null, calc6, PaymentMethod.CASH), "click an order"));
        check("after refused attempts: table 6 OCCUPIED, order SERVED, no bill", tbl(6).getStatus() == TableStatus.OCCUPIED && count("SELECT COUNT(*) FROM bills WHERE order_id = " + o6) == 0 && ready(o6) != null);

        // ---- the items changed after the cashier looked at the bill
        orders.addItem(o6, dish("Masala Chai"), 1); // order is SERVED but still open for corrections
        check("order changed while billing: payment refused, nothing saved",
                refused(() -> billing.pay(s6, calc6, PaymentMethod.CASH), "changed") && count("SELECT COUNT(*) FROM bills WHERE order_id = " + o6) == 0 && tbl(6).getStatus() == TableStatus.OCCUPIED);
        BillCalculation fresh6 = billing.calculate(billing.getLines(o6), DiscountType.NONE, "", "5");
        check("recalculated bill now includes the change (860.00 + GST 43.00 = 903.00)", same(fresh6.getSubtotal(), "860.00") && same(fresh6.getGrandTotal(), "903.00"));

        // ---- failed payment must roll back everything and keep the table OCCUPIED
        // fault injection: rename the table-status column so the LAST step of the payment (freeing the table) fails
        exec("ALTER TABLE restaurant_tables RENAME COLUMN status TO status_broken");
        boolean failed = false;
        try { billing.pay(ready(o6), fresh6, PaymentMethod.CASH); } catch (DatabaseException e) { failed = true; System.out.println("      (expected error shown to the user: " + e.getMessage() + ")"); }
        exec("ALTER TABLE restaurant_tables RENAME COLUMN status_broken TO status");
        check("simulated database failure in the LAST step: payment reports an error", failed);
        check("ROLLBACK: no bill saved", count("SELECT COUNT(*) FROM bills WHERE order_id = " + o6) == 0);
        check("ROLLBACK: order is still SERVED (not PAID)", count("SELECT COUNT(*) FROM orders WHERE order_id = " + o6 + " AND status = 'SERVED'") == 1);
        check("ROLLBACK: table 6 is still OCCUPIED", tbl(6).getStatus() == TableStatus.OCCUPIED);
        Bill b6 = billing.pay(ready(o6), fresh6, PaymentMethod.CASH);
        check("after the problem is gone the same bill can be paid normally, table 6 AVAILABLE", b6.getBillId() > 0 && tbl(6).getStatus() == TableStatus.AVAILABLE);

        // ---- never release a table that another open order still uses
        int o7 = servedOrder(7, "Meena Iyer");
        exec("INSERT INTO orders (table_id, customer_name, status) VALUES (" + tbl(7).getId() + ", 'Unrelated Guest', 'PENDING')");
        Bill b7 = billing.pay(ready(o7), billing.calculate(billing.getLines(o7), DiscountType.NONE, "", "5"), PaymentMethod.CASH);
        check("payment saved, but table 7 stays OCCUPIED because another open order uses it", b7.getBillId() > 0 && tbl(7).getStatus() == TableStatus.OCCUPIED);

        // ---- other tables untouched
        check("table 1 (READY order, never billed) is still OCCUPIED", tbl(1).getStatus() == TableStatus.OCCUPIED);
        check("tables 2, 8, 9 untouched (AVAILABLE)", tbl(2).getStatus() == TableStatus.AVAILABLE && tbl(8).getStatus() == TableStatus.AVAILABLE && tbl(9).getStatus() == TableStatus.AVAILABLE);

        // ---- history
        List<Bill> recent = billing.getRecentBills();
        check("paid bills list: 5 bills, newest first", recent.size() == 5 && recent.get(0).getBillId() == b7.getBillId());
        check("5 bills with payment status PAID in the database", count("SELECT COUNT(*) FROM bills WHERE payment_status = 'PAID'") == 5);
        Bill again = billing.getBill(b3.getBillId());
        check("a bill can be loaded again with items and receipt", again != null && again.getLines().size() == 2 && billing.buildReceipt(again).contains("₹765.45"));
        check("unknown bill number gives null", billing.getBill(999999) == null);
    }

    // ---------------- after restart ----------------
    static void phaseB() throws Exception {
        List<Bill> recent = billing.getRecentBills();
        check("after restart: 5 paid bills still saved", recent.size() == 5);
        Bill cash = recent.stream().filter(b -> b.getTableNumber() == 3).findFirst().orElseThrow();
        check("after restart: table 3 bill = CASH, 10% discount, total 765.45, PAID", cash.getPaymentMethod() == PaymentMethod.CASH && same(cash.getCalculation().getGrandTotal(), "765.45") && cash.getCalculation().getDiscountType() == DiscountType.PERCENT && cash.getPaymentStatus().equals("PAID"));
        check("after restart: payment methods saved: CASH, UPI, CARD all present", recent.stream().map(Bill::getPaymentMethod).collect(java.util.stream.Collectors.toSet()).containsAll(Set.of(PaymentMethod.CASH, PaymentMethod.UPI, PaymentMethod.CARD)));
        check("after restart: tables 3,4,5,6 AVAILABLE, table 1 and 7 OCCUPIED", tbl(3).getStatus() == TableStatus.AVAILABLE && tbl(4).getStatus() == TableStatus.AVAILABLE && tbl(5).getStatus() == TableStatus.AVAILABLE && tbl(6).getStatus() == TableStatus.AVAILABLE && tbl(1).getStatus() == TableStatus.OCCUPIED && tbl(7).getStatus() == TableStatus.OCCUPIED);
        check("after restart: paid orders are PAID, nothing is waiting for billing", count("SELECT COUNT(*) FROM orders WHERE status = 'PAID'") == 5 && billing.getOrdersReadyForBilling().isEmpty());
        check("after restart: receipt can be rebuilt with all items", billing.buildReceipt(billing.getBill(cash.getBillId())).contains("Butter Chicken"));
        check("after restart: Dashboard revenue still counts the bills", count("SELECT COUNT(*) FROM bills") == 5 && new DashboardDaoImpl().findRecentOrders(10).size() > 0);
    }
}

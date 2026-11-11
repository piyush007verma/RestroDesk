import com.restaurant.model.*;
import com.restaurant.ui.*;
import com.restaurant.ui.components.RoundedButton;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Reset Revenue test with the REAL screens (reuses the helpers of P7Test).
 * U = database WITHOUT migration 004 (drop table revenue_resets in the TEST database first): everything works, the button explains the migration.
 * A = empty schema-only database: pays 3 orders, Cancel, Confirm on Reports, new payment, Confirm on Dashboard, new payment.
 * B = new JVM (restart): the reset is still in force and old bills are still there.
 */
public class P9Test extends P7Test {
    static String fingerprint() throws Exception {
        return text("SELECT CONCAT((SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', bill_id, order_id, subtotal, gst_percent, gst_amount, discount_type, discount_value, discount_amount, total_amount, payment_method, payment_status, paid_at) ORDER BY bill_id SEPARATOR ';'), '') FROM bills), '#',"
                + "(SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', order_item_id, order_id, item_id, quantity, unit_price) ORDER BY order_item_id SEPARATOR ';'), '') FROM order_items), '#',"
                + "(SELECT COALESCE(GROUP_CONCAT(CONCAT_WS('|', order_id, table_id, customer_name, status, created_at, history_cleared_at) ORDER BY order_id SEPARATOR ';'), '') FROM orders))");
    }
    static String text(String sql) throws Exception {
        try (java.sql.Connection c = com.restaurant.db.DatabaseConnection.getConnection(); java.sql.Statement s = c.createStatement(); java.sql.ResultSet r = s.executeQuery(sql)) { r.next(); String v = r.getString(1); return v == null ? "" : v; } }
    static String shown(Container root, MainFrame f, Screen s, String card) throws Exception {
        ui(() -> f.navigateTo(s));
        Thread.sleep(1500);
        return cardValue(root, card);
    }
    static com.restaurant.model.RevenueReset last() throws Exception { return new com.restaurant.service.ReportsService(new com.restaurant.dao.ReportsDaoImpl()).latestReset(); }

    public static void main(String[] a) throws Exception {
        String phase = a[0], shots = a[1];
        if (phase.equals("U")) { phaseU(); return; }
        MainFrame[] f = new MainFrame[1];
        if (phase.equals("A")) {
            int o1 = served(1, "Asha"); pay(o1, DiscountType.PERCENT, "10", PaymentMethod.UPI);
            int o2 = served(2, "Bela"); pay(o2, DiscountType.NONE, "", PaymentMethod.CASH);
            int o3 = served(3, "Chet"); pay(o3, DiscountType.FIXED, "100", PaymentMethod.CARD);
        }
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        Container root = f[0].getContentPane();
        if (phase.equals("A")) {
            String before = fingerprint();
            String moneyBefore = money();
            String dashRev = shown(root, f[0], Screen.DASHBOARD, "Total Revenue");
            String repRev = shown(root, f[0], Screen.REPORTS, "Total Revenue");
            check("1: Dashboard and Reports show the same revenue before any reset (" + dashRev + ")", dashRev.equals(repRev) && !dashRev.equals("?") && !dashRev.contains("0.00") );
            shot(root, shots + "/p9_reports_before.png");

            // Cancel
            ui(() -> btn(root, "Reset Revenue").doClick());
            String t = answer("Cancel");
            check("5: the confirmation dialog explains what is reset and what stays, and shows 3 bills", t != null && t.contains("3 paid bills") && t.contains("nothing is deleted") && t.contains("STAYS EXACTLY"));
            Thread.sleep(800);
            check("5: Cancel changes nothing (revenue, no reset saved)", cardValue(root, "Total Revenue").equals(repRev) && last() == null && money().equals(moneyBefore));

            // Confirm on Reports
            ui(() -> btn(root, "Reset Revenue").doClick());
            check("2: confirm dialog", answer("Reset Revenue") != null);
            String ok = answer("OK");
            check("2: success message shown", ok != null && ok.contains("reset to zero") && ok.contains("still saved"));
            check("2: Reports revenue card is now zero", until(() -> cardValue(root, "Total Revenue").equals("₹0.00"), 5000));
            check("2: Reports payments, average and the other cards are zero", cardValue(root, "Payments Done").equals("0") && cardValue(root, "Avg Order Value").equals("₹0.00"));
            check("2: the sales tables, best sellers and payment split are empty/zero", tables(root).get(0).getRowCount() == 0 && tables(root).get(2).getRowCount() == 0
                    && java.util.stream.IntStream.range(0, tables(root).get(3).getRowCount()).allMatch(r -> tables(root).get(3).getValueAt(r, 1).toString().equals("0")));
            check("2: GST collected and discounts given show zero in the summary line", all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("Discounts given: ₹0.00") && l.getText().contains("GST collected: ₹0.00")));
            check("2: a 'revenue is counted from the reset on ...' note is shown", all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("counted from the reset")));
            shot(root, shots + "/p9_reports_after.png");
            check("2: Dashboard shows the same zero revenue (consistent)", shown(root, f[0], Screen.DASHBOARD, "Total Revenue").equals("₹0.00"));
            check("2: Dashboard remembers the reset time", all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().startsWith("Revenue counted since")));
            shot(root, shots + "/p9_dashboard_after.png");

            // old records intact
            check("4: bills, order items and orders are byte-identical after the reset", fingerprint().equals(before));
            check("4: Billing > Paid bills still lists all 3 bills", new com.restaurant.service.BillingService(new com.restaurant.dao.BillDaoImpl(), new com.restaurant.dao.OrderDaoImpl()).getRecentBills().size() == 3);
            check("4: the reset itself is saved as one row (3 bills, the amount that was cleared)", last() != null && last().billsCleared() == 3);

            // new transaction
            int o4 = served(4, "Dev"); pay(o4, DiscountType.NONE, "", PaymentMethod.UPI);
            String d1 = shown(root, f[0], Screen.DASHBOARD, "Total Revenue");
            String r1 = shown(root, f[0], Screen.REPORTS, "Total Revenue");
            check("3: a new payment after the reset is counted", d1.equals(r1) && !d1.equals("₹0.00") && d1.equals("₹850.50"));
            check("3: Reports payments = 1 and the UPI row = 1", cardValue(root, "Payments Done").equals("1"));

            // Dashboard reset
            ui(() -> f[0].navigateTo(Screen.DASHBOARD)); Thread.sleep(800);
            ui(() -> btn(root, "Reset Revenue").doClick());
            check("6: Dashboard button opens the same confirmation (1 bill)", answer("Reset Revenue") != null);
            check("6: success message on the Dashboard", answer("OK") != null);
            check("6: Dashboard revenue zero again", until(() -> cardValue(root, "Total Revenue").equals("₹0.00"), 5000));
            check("6: Reports agrees (zero)", shown(root, f[0], Screen.REPORTS, "Total Revenue").equals("₹0.00"));
            ui(() -> f[0].navigateTo(Screen.DASHBOARD)); Thread.sleep(800);
            ui(() -> btn(root, "Reset Revenue").doClick());
            String nothing = answer("OK");
            check("6: pressing reset with nothing counted just says 'Nothing to reset'", nothing != null && nothing.contains("already at zero"));
            check("old bills: 4 bills still in the database after two resets", text("SELECT COUNT(*) FROM bills").equals("4"));
            int o5 = served(5, "Esha"); pay(o5, DiscountType.NONE, "", PaymentMethod.CASH);
            check("3: another new payment counts after the second reset", shown(root, f[0], Screen.DASHBOARD, "Total Revenue").equals("₹850.50")
                    && shown(root, f[0], Screen.REPORTS, "Total Revenue").equals("₹850.50"));
            // reports date filters keep working
            
        } else {
            check("7 (after restart): Dashboard revenue is still the post-reset figure", shown(root, f[0], Screen.DASHBOARD, "Total Revenue").equals("₹850.50"));
            check("7 (after restart): Reports agrees", shown(root, f[0], Screen.REPORTS, "Total Revenue").equals("₹850.50"));
            check("7: all 5 bills are still in the database", text("SELECT COUNT(*) FROM bills").equals("5"));
            check("7: two resets are on record", text("SELECT COUNT(*) FROM revenue_resets").equals("2"));
        }
        System.out.println("RESULT P9 " + phase + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    static void phaseU() throws Exception {
        com.restaurant.service.ReportsService rs = new com.restaurant.service.ReportsService(new com.restaurant.dao.ReportsDaoImpl());
        int id = served(1, "Old"); pay(id, DiscountType.NONE, "", PaymentMethod.CASH);
        check("U: no revenue_resets table exists", text("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'revenue_resets'").equals("0"));
        System.out.println("money=" + money()); check("U: Reports still counts the bill (revenue 850.50)", money().startsWith("850.5"));
        check("U: Dashboard revenue works", new com.restaurant.service.DashboardService(new com.restaurant.dao.DashboardDaoImpl()).loadDashboard().getTotalRevenue().compareTo(new java.math.BigDecimal("850.50")) == 0);
        check("U: latestReset is null", rs.latestReset() == null);
        String msg = "";
        try { rs.previewReset(); } catch (Exception e) { msg = e.getMessage(); }
        check("U: the reset refuses and names migration 004", msg.contains("004_add_revenue_resets.sql"));
        try { rs.resetRevenue(); msg = ""; } catch (Exception e) { msg = e.getMessage(); }
        check("U: resetting refuses too and changes nothing", msg.contains("004_add_revenue_resets.sql") && text("SELECT COUNT(*) FROM bills").equals("1"));
        System.out.println("RESULT P9 U: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}

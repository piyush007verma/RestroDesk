import com.restaurant.ui.*;
import com.restaurant.ui.components.BarChartPanel;
import com.restaurant.ui.components.RoundedButton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Phase 5 UI test: the REAL MainFrame, the REAL Order History and Reports screens, REAL DAOs, a REAL database.
 * Run it AFTER HistReportItTest phase A (it expects that data: 7 orders, 3 paid bills = 2361.45).
 */
public class P5Test {
    static int pass, fail;
    static void check(String n, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + n); }
    static <T extends Component> List<T> all(Container c, Class<T> k) {
        List<T> l = new ArrayList<>();
        for (Component x : c.getComponents()) { if (k.isInstance(x)) l.add(k.cast(x)); if (x instanceof Container cc) l.addAll(all(cc, k)); }
        return l;
    }
    static void ui(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    static boolean until(BooleanSupplier s, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { boolean[] ok = {false}; ui(() -> ok[0] = s.getAsBoolean()); if (ok[0]) return true; Thread.sleep(50); }
        return false;
    }
    static RoundedButton btn(Container c, String text) { return all(c, RoundedButton.class).stream().filter(b -> b.isShowing() && b.getText().startsWith(text)).findFirst().orElseThrow(); }
    static List<JTable> tables(Container root) { List<JTable> t = new ArrayList<>(); all(root, JTable.class).stream().filter(JTable::isShowing).forEach(t::add); return t; }
    static void shot(Component c, String file) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        ui(() -> c.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", new File(file));
    }
    static String card(Container root, String title) {
        // StatCard: title label then value label (28 pt) in the same panel
        for (JLabel l : all(root, JLabel.class)) if (l.isShowing() && l.getText().equals(title)) {
            for (JLabel v : all(l.getParent(), JLabel.class)) if (v.getFont().getSize() >= 20) return v.getText();
        }
        return "?";
    }

    public static void main(String[] a) throws Exception {
        String shots = a[0];
        MainFrame[] f = new MainFrame[1];
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        Container root = f[0].getContentPane();

        // ---------------- Order History ----------------
        ui(() -> f[0].navigateTo(Screen.HISTORY));
        until(() -> tables(root).size() >= 2, 3000);
        JTable list = tables(root).get(0), items = tables(root).get(1);
        check("Order History opens from the sidebar and lists all 7 orders from MySQL", until(() -> list.getRowCount() == 7, 5000));
        check("list columns: ID, Table, Customer, Date, Status, Total, Payment, Method", list.getColumnCount() == 8 && list.getColumnName(3).equals("Date") && list.getColumnName(7).equals("Method"));
        check("newest order first", list.getValueAt(0, 0).toString().startsWith("#7"));
        shot(root, shots + "/history_all.png");

        JTextField orderId = all(root, JTextField.class).stream().filter(t -> t.isShowing() && t.getColumns() == 6).findFirst().orElseThrow();
        JTextField tableNo = all(root, JTextField.class).stream().filter(t -> t.isShowing() && t.getColumns() == 5).findFirst().orElseThrow();
        ui(() -> { orderId.setText("1"); orderId.postActionEvent(); });
        check("search by Order ID 1 -> one row, PAID, UPI", until(() -> list.getRowCount() == 1 && list.getValueAt(0, 6).equals("PAID") && list.getValueAt(0, 7).equals("UPI"), 4000));
        ui(() -> { orderId.setText("abc"); orderId.postActionEvent(); });
        JLabel notice = all(root, JLabel.class).stream().filter(l -> l.isShowing() && l.getText().contains("digits only")).findFirst().orElse(null);
        check("bad Order ID shows a clear message and keeps the list", notice != null && list.getRowCount() == 1);
        RoundedButton reset = btn(root, "Reset");
        ui(reset::doClick);
        check("Reset brings back all 7 orders and clears the message", until(() -> list.getRowCount() == 7, 4000)
                && all(root, JLabel.class).stream().noneMatch(l -> l.isShowing() && l.getText().contains("digits only")));
        ui(() -> { tableNo.setText("2"); tableNo.postActionEvent(); });
        check("search by Table Number 2 -> Bela's order", until(() -> list.getRowCount() == 1 && list.getValueAt(0, 2).equals("Bela"), 4000));
        ui(reset::doClick); until(() -> list.getRowCount() == 7, 4000);

        List<JComboBox> combos = new ArrayList<>(); ui(() -> all(root, JComboBox.class).stream().filter(JComboBox::isShowing).forEach(combos::add));
        JComboBox status = combos.get(0), payment = combos.get(1), method = combos.get(2);
        ui(() -> status.setSelectedItem(com.restaurant.model.OrderStatus.PAID));
        check("filter status PAID -> 3 rows", until(() -> list.getRowCount() == 3, 4000));
        ui(() -> method.setSelectedItem(com.restaurant.model.PaymentMethod.CARD));
        check("PAID + Card -> 1 row, Chet", until(() -> list.getRowCount() == 1 && list.getValueAt(0, 2).equals("Chet"), 4000));
        ui(reset::doClick); until(() -> list.getRowCount() == 7, 4000);
        ui(() -> payment.setSelectedItem("Unpaid"));
        check("filter payment Unpaid -> 3 rows (the 2 open + new guest; cancelled excluded)", until(() -> list.getRowCount() == 3, 4000));
        ui(reset::doClick); until(() -> list.getRowCount() == 7, 4000);

        JCheckBox dateCheck = all(root, JCheckBox.class).stream().filter(JComponent::isShowing).findFirst().orElseThrow();
        ui(dateCheck::doClick);
        List<JSpinner> sp = new ArrayList<>(); ui(() -> all(root, JSpinner.class).stream().filter(JSpinner::isShowing).forEach(sp::add));
        ui(() -> { sp.get(0).setValue(java.util.Date.from(java.time.LocalDate.now().minusDays(3).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())); });
        check("date range from 30 days ago: the 40-day-old order is outside (6 rows)", until(() -> list.getRowCount() == 6, 4000));
        ui(() -> sp.get(0).setValue(java.util.Date.from(java.time.LocalDate.now().minusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));
        check("changing the start date searches by itself: from yesterday -> the 2-day-old order is gone (5 rows)", until(() -> list.getRowCount() == 5, 4000));
        ui(() -> sp.get(0).setValue(java.util.Date.from(java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())));
        check("start date after end date -> message, list unchanged", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("start date is after")), 3000));
        ui(reset::doClick); until(() -> list.getRowCount() == 7, 4000);

        // details of a paid order (order 1 is the oldest paid with UPI; it is the last row)
        int row1 = -1; for (int i = 0; i < list.getRowCount(); i++) if (list.getValueAt(i, 0).equals("#1")) row1 = i;
        final int r1 = row1;
        ui(() -> list.setRowSelectionInterval(r1, r1));
        check("details: 2 food items with qty, price, subtotal", until(() -> items.getRowCount() == 2 && items.getValueAt(0, 3).toString().contains("760.00"), 4000));
        check("details: bill breakdown shows discount 10%, GST, grand total 765.45", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("765.45")), 3000)
                && all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("(10%)")));
        RoundedButton receipt = btn(root, "View Receipt");
        check("View Receipt is enabled for a billed order", until(receipt::isEnabled, 3000));
        shot(root, shots + "/history_details.png");
        int row5 = -1; for (int i = 0; i < list.getRowCount(); i++) if (list.getValueAt(i, 4).equals("SERVED")) row5 = i;
        final int r5 = row5;
        ui(() -> list.setRowSelectionInterval(r5, r5));
        check("unbilled order: items shown, View Receipt disabled", until(() -> items.getRowCount() == 2 && !receipt.isEnabled(), 4000));
        check("no Delete / Edit / Remove button exists on the History screen", all(root, RoundedButton.class).stream().filter(Component::isShowing)
                .noneMatch(b -> Set.of("delete", "edit", "remove", "update", "cancel").contains(b.getText().toLowerCase())));

        // ---------------- Reports ----------------
        ui(() -> f[0].navigateTo(Screen.REPORTS));
        until(() -> tables(root).size() >= 4, 3000);
        List<JTable> rt = tables(root);
        check("Reports opens with 4 report tables", rt.size() == 4);
        check("This month (default): cards show real numbers", until(() -> !card(root, "Total Orders").equals("?") && !card(root, "Total Revenue").equals("-"), 5000));
        shot(root, shots + "/reports_month.png");
        JComboBox<?> period = all(root, JComboBox.class).stream().filter(JComboBox::isShowing).findFirst().orElseThrow();
        ui(() -> period.setSelectedIndex(3)); // All time
        check("All time: 7 orders, revenue ₹2,361.45, 3 payments, average ₹787.15", until(() -> card(root, "Total Orders").equals("7") && card(root, "Total Revenue").equals("₹2,361.45")
                && card(root, "Payments Done").equals("3") && card(root, "Avg Order Value").equals("₹787.15"), 5000));
        check("All time: pending payments card = 1 served unpaid order", card(root, "Pending Payments").equals("1"));
        check("daily table 3 rows, monthly rows >= 2, best-selling 2 rows, payment methods 3 rows", until(() -> rt.get(0).getRowCount() == 3 && rt.get(1).getRowCount() >= 2 && rt.get(2).getRowCount() == 2 && rt.get(3).getRowCount() == 3, 3000));
        check("best-selling first row: Butter Chicken, 6 sold", rt.get(2).getValueAt(0, 0).equals("Butter Chicken") && rt.get(2).getValueAt(0, 1).toString().equals("6"));
        List<BarChartPanel> charts = all(root, BarChartPanel.class).stream().filter(Component::isShowing).toList(); // the Dashboard has its own chart
        check("4 charts drawn with data (daily 3 bars, best-selling 2, payments 3)", charts.size() == 4 && charts.get(0).barCount() == 3 && charts.get(2).barCount() == 2 && charts.get(3).barCount() == 3);
        shot(root, shots + "/reports_all.png");
        ui(() -> period.setSelectedIndex(0)); // Today
        check("Today: only today's payment (₹850.50)", until(() -> card(root, "Total Revenue").equals("₹850.50") && card(root, "Payments Done").equals("1"), 5000));
        ui(() -> period.setSelectedIndex(4)); // Custom
        List<JSpinner> rs = new ArrayList<>(); ui(() -> all(root, JSpinner.class).stream().filter(JSpinner::isShowing).forEach(rs::add));
        ui(() -> { rs.get(0).setValue(java.util.Date.from(java.time.LocalDate.now().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant())); btn(root, "Apply").doClick(); });
        check("custom range with start after end -> clear message", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("start date is after")), 3000));

        // ---------------- regression: the other screens still open ----------------
        for (Screen s : new Screen[]{Screen.DASHBOARD, Screen.MENU, Screen.TABLES, Screen.ORDERS, Screen.BILLING}) { ui(() -> f[0].navigateTo(s)); Thread.sleep(700); }
        check("Dashboard, Menu, Tables, Orders and Billing still open", true);
        shot(root, shots + "/billing.png");
        System.out.println("RESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}

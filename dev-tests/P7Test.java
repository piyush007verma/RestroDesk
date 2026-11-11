import com.restaurant.dao.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import com.restaurant.ui.*;
import com.restaurant.ui.components.RoundedButton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Final-refinement test: the 14-step "global Order History clearing" sequence with the REAL MainFrame, REAL services and DAOs.
 * Phase A (empty database made with schema.sql + sample_data.sql): creates orders, clears, checks Dashboard / History / Billing / Orders / Reports.
 * Phase B (a NEW JVM = application restarted): everything is still hidden, a new order shows up, money is unchanged.
 * Never run it on a database whose data you want to keep.
 */
public class P7Test {
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
        while (System.currentTimeMillis() < end) { boolean[] ok = {false}; ui(() -> ok[0] = s.getAsBoolean()); if (ok[0]) return true; Thread.sleep(60); }
        return false;
    }
    static RoundedButton btn(Container c, String text) { return all(c, RoundedButton.class).stream().filter(b -> b.isShowing() && b.getText().startsWith(text)).findFirst().orElseThrow(); }
    static List<JTable> tables(Container root) { return all(root, JTable.class).stream().filter(JTable::isShowing).toList(); }
    static void shot(Component c, String file) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        ui(() -> c.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", new File(file));
    }
    static String lastDefault = "";
    static String answer(String button) throws Exception {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    Optional<JButton> b = all(d.getContentPane(), JButton.class).stream().filter(x -> x.getText().equals(button)).findFirst();
                    if (b.isPresent()) {
                        JOptionPane pane = all(d.getContentPane(), JOptionPane.class).stream().findFirst().orElse(null);
                        String text = String.valueOf(pane == null ? "" : pane.getMessage());
                        ui(b.get()::doClick);
                        return text;
                    }
                }
            }
            Thread.sleep(50);
        }
        return null;
    }
    /** Order ids shown in the first column of a table (text like "#3" or "#3*"). */
    static Set<Integer> idsIn(JTable t) {
        Set<Integer> s = new TreeSet<>();
        for (int r = 0; r < t.getRowCount(); r++) s.add(Integer.parseInt(t.getValueAt(r, 0).toString().replaceAll("[^0-9]", "")));
        return s;
    }
    static String cardValue(Container root, String title) {
        for (JLabel l : all(root, JLabel.class)) if (l.isShowing() && l.getText().equals(title)) {
            for (JLabel v : all(l.getParent(), JLabel.class)) if (v != l && v.getFont().getSize() >= 20) return v.getText();
        }
        return "?";
    }

    static BillingService billing = new BillingService(new BillDaoImpl(), new OrderDaoImpl());
    static OrderService orders = new OrderService(new OrderDaoImpl());
    static TableService tablesSvc = new TableService(new TableDaoImpl());
    static MenuService menu = new MenuService(new MenuDaoImpl());
    static ReportsService reports = new ReportsService(new ReportsDaoImpl());
    static TableOverview tbl(int n) throws Exception { return tablesSvc.getTables().stream().filter(t -> t.getNumber() == n).findFirst().orElseThrow(); }
    static OrderSummary active(int id) throws Exception { return orders.getActiveOrders().stream().filter(o -> o.getOrderId() == id).findFirst().orElse(null); }
    static com.restaurant.model.MenuItem dish(String n) throws Exception { return menu.getAllItems().stream().filter(m -> m.getName().equals(n)).findFirst().orElseThrow(); }
    static int open(int table, String who) throws Exception {
        int id = tablesSvc.assignTable(tbl(table).getId(), who, null);
        orders.addItem(id, dish("Butter Chicken"), 2); orders.addItem(id, dish("Masala Chai"), 1);
        return id;
    }
    static int served(int table, String who) throws Exception {
        int id = open(table, who);
        for (OrderStatus s : new OrderStatus[]{OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED}) orders.changeStatus(active(id), s);
        return id;
    }
    static void pay(int id, DiscountType t, String d, PaymentMethod m) throws Exception {
        OrderSummary o = billing.getOrdersReadyForBilling().stream().filter(x -> x.getOrderId() == id).findFirst().orElseThrow();
        billing.pay(o, billing.calculate(billing.getLines(id), t, d, "5"), m);
    }
    static String money() throws Exception {
        ReportSummary s = reports.load(reports.resolve(ReportsService.Period.ALL_TIME, null, null, java.time.LocalDate.now())).summary();
        return s.revenue() + "/" + s.gst() + "/" + s.discounts() + "/" + s.completedPayments();
    }

    public static void main(String[] a) throws Exception {
        String phase = a[0], shots = a[1];
        MainFrame[] f = new MainFrame[1];
        int[] id = new int[8];
        int base = 0;
        if (phase.equals("A")) {
            id[1] = served(1, "Asha"); pay(id[1], DiscountType.PERCENT, "10", PaymentMethod.UPI);
            id[2] = served(2, "Bela"); pay(id[2], DiscountType.NONE, "", PaymentMethod.CASH);
            id[3] = served(3, "Chet"); // served, unpaid -> stays visible
            id[4] = open(4, "Dev");    // pending -> stays visible
            System.out.println("IDS " + id[1] + " " + id[2] + " " + id[3] + " " + id[4]);
        }
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        Container root = f[0].getContentPane();
        String moneyBefore = money();
        if (phase.equals("A")) {
            // 2-3: orders appear in Dashboard and in Order History
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            until(() -> !tables(root).isEmpty() && tables(root).get(0).getRowCount() >= 4, 5000);
            JTable dash = tables(root).get(0);
            Set<Integer> all4 = new TreeSet<>(List.of(id[1], id[2], id[3], id[4]));
            check("step 2: the 4 new orders appear in Dashboard > Recent Orders", idsIn(dash).containsAll(all4));
            check("Dashboard Total Orders card counts them", until(() -> Integer.parseInt(cardValue(root, "Total Orders")) >= 4, 3000));
            shot(root, shots + "/p7_dashboard_before.png");
            ui(() -> f[0].navigateTo(Screen.HISTORY));
            until(() -> tables(root).size() >= 2 && tables(root).get(0).getRowCount() >= 4, 5000);
            JTable hist = tables(root).get(0);
            check("step 3: the same orders appear in Order History", idsIn(hist).containsAll(all4));

            // 4-5: clear, Cancel
            RoundedButton clear = btn(root, "Clear Order History");
            int dashBefore = idsIn(dash).size(), histBefore = hist.getRowCount();
            ui(clear::doClick);
            String t = answer("Cancel");
            check("step 4-5: confirmation dialog shows 2 finished orders; Cancel pressed", t != null && t.contains("2 finished"));
            Thread.sleep(700);
            check("Cancel changed nothing in Order History", hist.getRowCount() == histBefore);
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            Thread.sleep(1200);
            check("Cancel changed nothing in the Dashboard", idsIn(tables(root).get(0)).size() == dashBefore);

            // 6-7: confirm
            ui(() -> f[0].navigateTo(Screen.HISTORY));
            until(() -> tables(root).size() >= 2 && tables(root).get(0).getRowCount() >= 4, 5000);
            JTable hist2 = tables(root).get(0);
            ui(() -> btn(root, "Clear Order History").doClick());
            check("step 6: confirm dialog", answer("Clear History") != null);
            check("success message shown", answer("OK") != null);
            check("step 7: Order History now lists only the open orders", until(() -> idsIn(hist2).equals(new TreeSet<>(List.of(id[3], id[4]))), 5000));
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            check("step 7: Dashboard > Recent Orders shows only the open orders (cleared ones are gone)",
                    until(() -> idsIn(tables(root).get(0)).equals(new TreeSet<>(List.of(id[3], id[4]))), 5000));
            check("Dashboard Total Orders card = 2 (same rule as the lists)", until(() -> cardValue(root, "Total Orders").equals("2"), 3000));
            shot(root, shots + "/p7_dashboard_after.png");

            // 8: other sections
            ui(() -> f[0].navigateTo(Screen.ORDERS));
            until(() -> !tables(root).isEmpty(), 3000); Thread.sleep(800);
            check("step 8: Orders screen lists the open orders only", idsIn(tables(root).get(0)).equals(new TreeSet<>(List.of(id[3], id[4]))));
            ui(() -> f[0].navigateTo(Screen.BILLING));
            until(() -> !tables(root).isEmpty(), 3000); Thread.sleep(800);
            check("Billing > orders ready to bill still lists the served order", idsIn(tables(root).get(0)).contains(id[3]));
            check("Billing > Paid bills still has both bills (bills are never hidden)", billing.getRecentBills().size() == 2);
            check("step 13-14: revenue, GST, discounts and payments unchanged", money().equals(moneyBefore));
            ui(() -> f[0].navigateTo(Screen.REPORTS));
            Thread.sleep(1500);
            check("Reports screen still shows the revenue", cardValue(root, "Total Revenue").contains("1,"));
            check("Reports > Total Orders also drops to 2 after clearing (cleared orders are not counted)", until(() -> cardValue(root, "Total Orders").equals("2"), 4000));

            // 11-12: a new order after clearing
            id[5] = open(5, "Esha");
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            check("step 11-12: a NEW order appears normally in the Dashboard after clearing",
                    until(() -> idsIn(tables(root).get(0)).contains(id[5]), 5000));
            ui(() -> f[0].navigateTo(Screen.HISTORY));
            until(() -> tables(root).size() >= 2, 3000); Thread.sleep(800);
            check("... and in Order History", idsIn(tables(root).get(0)).contains(id[5]));
            // clickable card + quick action + shortcut
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            ui(() -> btn(root, "View Reports").doClick());
            check("quick action 'View Reports' opens Reports", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().equals("Daily sales")), 3000));
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            StatCardClick.click(root, f[0]);
            check("clicking the Total Revenue card opens Reports", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().equals("Daily sales")), 3000));
            System.out.println("IDSX " + id[3] + " " + id[4] + " " + id[5] + " money " + moneyBefore);
            try (var w = new java.io.PrintWriter(shots + "/p7_state.txt")) { w.println(id[1] + " " + id[2] + " " + id[3] + " " + id[4] + " " + id[5]); w.println(moneyBefore); }
        } else {
            String[] st = java.nio.file.Files.readAllLines(new File(shots + "/p7_state.txt").toPath()).toArray(new String[0]);
            String[] ids = st[0].split(" ");
            int c1 = Integer.parseInt(ids[0]), c2 = Integer.parseInt(ids[1]), o3 = Integer.parseInt(ids[2]), o4 = Integer.parseInt(ids[3]), o5 = Integer.parseInt(ids[4]);
            ui(() -> f[0].navigateTo(Screen.DASHBOARD));
            until(() -> !tables(root).isEmpty() && tables(root).get(0).getRowCount() >= 3, 5000);
            Set<Integer> shown = idsIn(tables(root).get(0));
            check("step 9-10 (after restart): Dashboard still hides the cleared orders", !shown.contains(c1) && !shown.contains(c2) && shown.containsAll(Set.of(o3, o4, o5)));
            ui(() -> f[0].navigateTo(Screen.HISTORY));
            until(() -> tables(root).size() >= 2 && tables(root).get(0).getRowCount() >= 3, 5000);
            Set<Integer> h = idsIn(tables(root).get(0));
            check("Order History still hides them after restart, new order visible", !h.contains(c1) && !h.contains(c2) && h.contains(o5));
            check("step 13-14 (after restart): revenue / GST / discounts / payments identical", money().equals(st[1]));
            check("bills still available after restart", billing.getRecentBills().size() == 2);
            JCheckBox cb = all(root, JCheckBox.class).stream().filter(c -> c.getText().equals("Show cleared orders")).findFirst().orElseThrow();
            ui(cb::doClick);
            check("'Show cleared orders' still brings the cleared ones back", until(() -> idsIn(tables(root).get(0)).containsAll(Set.of(c1, c2)), 4000));
        }
        System.out.println("RESULT P7 " + phase + ": " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    /** Clicks the Total Revenue card with a real mouse event. */
    static class StatCardClick {
        static void click(Container root, MainFrame f) throws Exception {
            for (JLabel l : all(root, JLabel.class)) if (l.isShowing() && l.getText().equals("Total Revenue")) {
                Container card = l.getParent();
                while (card != null && !(card instanceof com.restaurant.ui.components.StatCard)) card = card.getParent();
                final Container c = card;
                ui(() -> { for (var ml : c.getMouseListeners()) ml.mouseClicked(new java.awt.event.MouseEvent(c, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false)); });
                return;
            }
        }
    }
}

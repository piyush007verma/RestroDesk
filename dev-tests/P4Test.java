import com.restaurant.dao.*;
import com.restaurant.model.*;
import com.restaurant.service.*;
import com.restaurant.ui.*;
import com.restaurant.ui.components.RoundedButton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Phase 4 UI test: the REAL MainFrame, REAL Billing screen, REAL DAOs and a REAL database (config/db.properties in the current folder). Needs an empty restodesk database made with schema.sql. */
public class P4Test {
    static int pass, fail;
    static volatile int answer = JOptionPane.YES_OPTION;
    static final List<String[]> dialogs = Collections.synchronizedList(new ArrayList<>());
    static volatile String shotDir;
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
    static RoundedButton btn(Container c, String text) { return all(c, RoundedButton.class).stream().filter(b -> b.isShowing() && b.getText().startsWith(text)).findFirst().orElse(null); }
    static void shot(Component c, String file) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        ui(() -> c.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", new File(shotDir, file));
    }
    static void type(JTextField f, String text) throws Exception { ui(() -> f.setText(text)); }
    static String text(Container root, int ignored) { return all(root, JLabel.class).stream().filter(l -> l.isShowing() && "grandTotal".equals(l.getName())).map(JLabel::getText).findFirst().orElse("?"); }
    static boolean dialogHas(String t) { synchronized (dialogs) { return dialogs.stream().anyMatch(d -> d[0].contains(t) || d[1].contains(t)); } }
    static long dialogCount() { return dialogs.size(); }

    /** Answers every JOptionPane (Yes or No as set in `answer`), records receipt windows (and a picture of them) and closes them. */
    static void startWatcher() {
        Thread th = new Thread(() -> { while (true) {
            try { Thread.sleep(40); } catch (InterruptedException e) { return; }
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible()) {
                List<JTextArea> receipt = all(d.getContentPane(), JTextArea.class);
                List<JOptionPane> panes = all(d.getContentPane(), JOptionPane.class);
                JOptionPane p = panes.isEmpty() ? (d.getContentPane() instanceof JOptionPane jp ? jp : null) : panes.get(0);
                if (p != null) { final JOptionPane fp = p; dialogs.add(new String[]{d.getTitle(), String.valueOf(fp.getMessage())});
                    SwingUtilities.invokeLater(() -> fp.setValue(answer)); }
                else if (!receipt.isEmpty()) { dialogs.add(new String[]{d.getTitle(), receipt.get(0).getText()});
                    try { BufferedImage img = new BufferedImage(d.getWidth(), d.getHeight(), BufferedImage.TYPE_INT_RGB); d.paint(img.getGraphics());
                          javax.imageio.ImageIO.write(img, "png", new File(shotDir, "receipt_" + dialogs.size() + ".png")); } catch (Exception ignored) { }
                    SwingUtilities.invokeLater(d::dispose); }
            } } });
        th.setDaemon(true); th.start();
    }

    public static void main(String[] a) throws Exception {
        shotDir = a[0];
        startWatcher();
        MenuService menu = new MenuService(new MenuDaoImpl());
        TableService tables = new TableService(new TableDaoImpl());
        OrderService orders = new OrderService(new OrderDaoImpl());
        java.util.function.Function<Integer, TableOverview> tbl = n -> { try { return tables.getTables().stream().filter(t -> t.getNumber() == n).findFirst().orElseThrow(); } catch (Exception e) { throw new RuntimeException(e); } };
        com.restaurant.model.MenuItem chicken = menu.getAllItems().stream().filter(m -> m.getName().equals("Butter Chicken")).findFirst().orElseThrow();
        com.restaurant.model.MenuItem chai = menu.getAllItems().stream().filter(m -> m.getName().equals("Masala Chai")).findFirst().orElseThrow();
        int[] ids = new int[10];
        for (int t : new int[]{3, 4, 5}) {
            ids[t] = tables.assignTable(tbl.apply(t).getId(), "Guest " + "ABCDEFGHIJ".charAt(t), null);
            orders.addItem(ids[t], chicken, 2); orders.addItem(ids[t], chai, 1);
            if (t != 4) for (OrderStatus s : new OrderStatus[]{OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.SERVED})
                orders.changeStatus(orders.getActiveOrders().stream().filter(o -> o.getOrderId() == ids[tt(t)]).findFirst().orElseThrow(), s);
        }

        MainFrame[] f = new MainFrame[1];
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        ui(() -> f[0].navigateTo(Screen.BILLING));
        Container root = f[0].getContentPane();
        until(() -> all(root, JTable.class).stream().filter(JTable::isShowing).count() >= 2, 4000);
        List<JTable> t = new ArrayList<>();
        ui(() -> all(root, JTable.class).stream().filter(JTable::isShowing).forEach(t::add));
        JTable list = t.get(0), items = t.get(1);
        check("Billing opens from the sidebar (list + items tables)", t.size() == 2);
        check("only SERVED orders are listed: tables 3 and 5 (table 4 is still PENDING)", until(() -> list.getRowCount() == 2, 4000) && list.getValueAt(0, 1).equals("T3") && list.getValueAt(1, 1).equals("T5") && list.getValueAt(0, 5).equals("SERVED"));
        RoundedButton pay = btn(root, "Confirm Payment");
        check("Confirm Payment disabled before an order is selected", pay != null && !pay.isEnabled());

        ui(() -> list.setRowSelectionInterval(0, 0));
        check("selecting the order shows its 2 food items", until(() -> items.getRowCount() == 2 && items.getValueAt(0, 0).equals("Butter Chicken"), 4000));
        check("items show quantity, price and subtotal", items.getValueAt(0, 1).toString().equals("2") && items.getValueAt(0, 2).toString().contains("380.00") && items.getValueAt(0, 3).toString().contains("760.00"));
        check("grand total with default GST 5% = ₹850.50", until(() -> text(root, 24).contains("850.50"), 3000));
        check("Confirm Payment enabled for a valid bill", until(pay::isEnabled, 2000));

        List<JComboBox> combos = new ArrayList<>(); ui(() -> all(root, JComboBox.class).stream().filter(JComboBox::isShowing).forEach(combos::add));
        JComboBox<?> modeBox = combos.get(0), discountBox = combos.get(1), methodBox = combos.get(2);
        List<JTextField> fields = new ArrayList<>(); ui(() -> all(root, JTextField.class).stream().filter(x -> x.isShowing() && !(x.getParent() instanceof JComboBox)).forEach(fields::add));
        JTextField discountField = fields.get(0), gstField = fields.get(1);

        ui(() -> discountBox.setSelectedItem(DiscountType.PERCENT)); type(discountField, "10");
        check("10% discount: total ₹765.45", until(() -> text(root, 24).contains("765.45"), 3000));
        ui(() -> discountBox.setSelectedItem(DiscountType.FIXED)); type(discountField, "100");
        check("fixed ₹100 discount: total ₹745.50", until(() -> text(root, 24).contains("745.50"), 3000));
        type(discountField, "abc");
        check("discount 'abc': message shown and payment disabled", until(() -> !pay.isEnabled(), 2000) && all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().toLowerCase().contains("number")));
        type(discountField, "-5");
        check("negative discount: payment disabled", until(() -> !pay.isEnabled(), 2000));
        type(discountField, "5000");
        check("fixed discount above the subtotal: payment disabled with message", until(() -> !pay.isEnabled(), 2000) && all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().contains("more than the subtotal")));
        ui(() -> discountBox.setSelectedItem(DiscountType.PERCENT)); type(discountField, "150");
        check("percentage 150: payment disabled", until(() -> !pay.isEnabled(), 2000));
        type(discountField, "10"); type(gstField, "18");
        check("GST changed to 18%: total ₹860.22 (729.00 + 131.22)", until(() -> text(root, 24).contains("860.22"), 3000));
        type(gstField, "5");
        check("back to GST 5%: total ₹765.45 and payment enabled", until(() -> text(root, 24).contains("765.45") && pay.isEnabled(), 3000));
        shot(root, "billing_ready.png");

        // ----- cancelled payment keeps the table OCCUPIED
        answer = JOptionPane.NO_OPTION; long before = dialogCount();
        ui(() -> methodBox.setSelectedItem(PaymentMethod.UPI));
        ui(pay::doClick);
        check("payment confirmation question appears (with amount and method)", until(() -> dialogCount() > before, 3000) && dialogHas("765.45") && dialogHas("UPI"));
        Thread.sleep(600);
        check("payment CANCELLED (answer No): table 3 still OCCUPIED, order SERVED, still listed", tbl.apply(3).getStatus() == TableStatus.OCCUPIED && list.getRowCount() == 2 && orders.getActiveOrders().stream().anyMatch(o -> o.getOrderId() == ids[3] && o.getStatus() == OrderStatus.SERVED));
        check("selecting a payment method alone did not free the table", tbl.apply(3).getStatus() == TableStatus.OCCUPIED);

        // ----- confirmed payment
        answer = JOptionPane.YES_OPTION; long before2 = dialogCount();
        ui(pay::doClick);
        check("success message with bill number appears", until(() -> dialogHas("Payment successful") || dialogHas("Bill number"), 5000));
        check("receipt window opens with RestoDesk, items, discount, GST, total, UPI, PAID", until(() -> dialogHas("GRAND TOTAL"), 5000) && dialogHas("Butter Chicken") && dialogHas("Discount (10%)") && dialogHas("₹765.45") && dialogHas("UPI") && dialogHas("PAID"));
        check("AFTER payment: table 3 AVAILABLE", until(() -> tbl.apply(3).getStatus() == TableStatus.AVAILABLE, 4000));
        check("AFTER payment: table 4 (pending order) and table 5 (served, unpaid) stay OCCUPIED", tbl.apply(4).getStatus() == TableStatus.OCCUPIED && tbl.apply(5).getStatus() == TableStatus.OCCUPIED);
        check("paid order leaves the billing list (only table 5 left)", until(() -> list.getRowCount() == 1 && list.getValueAt(0, 1).equals("T5"), 4000));
        check("bill card is cleared after payment", until(() -> !pay.isEnabled() && items.getRowCount() == 0, 3000));

        // ----- paid bills history
        ui(() -> modeBox.setSelectedItem("Paid bills"));
        check("Paid bills mode lists the saved bill (method UPI, total 765.45)", until(() -> list.getRowCount() == 1 && list.getValueAt(0, 5).toString().equals("UPI") && list.getValueAt(0, 4).toString().contains("765.45"), 4000));
        ui(() -> list.setRowSelectionInterval(0, 0));
        RoundedButton view = btn(root, "View Receipt");
        check("selecting a paid bill shows its items and enables View Receipt", until(() -> items.getRowCount() == 2 && view != null && view.isEnabled(), 4000));
        check("paid bill is read-only: discount and payment inputs disabled", !discountField.isEnabled() && !methodBox.isEnabled() && !gstField.isEnabled());
        shot(root, "billing_paid.png");
        long before3 = dialogCount();
        ui(view::doClick);
        check("View Receipt opens the receipt again", until(() -> dialogCount() > before3 && dialogHas("Discount (10%)"), 4000));

        // ----- pay table 5 with CASH from the screen (second payment)
        ui(() -> modeBox.setSelectedItem("Ready to bill"));
        until(() -> list.getRowCount() == 1, 4000);
        ui(() -> list.setRowSelectionInterval(0, 0));
        until(() -> pay.isShowing() && pay.isEnabled(), 4000);
        ui(() -> methodBox.setSelectedItem(PaymentMethod.CASH));
        ui(pay::doClick);
        check("CASH payment of table 5 from the screen: table 5 AVAILABLE", until(() -> tbl.apply(5).getStatus() == TableStatus.AVAILABLE, 5000));
        check("table 4 (not served) is still OCCUPIED", tbl.apply(4).getStatus() == TableStatus.OCCUPIED);
        Thread.sleep(800);

        // ----- the other screens still open
        for (Screen s : new Screen[]{Screen.ORDERS, Screen.MENU, Screen.TABLES, Screen.DASHBOARD}) {
            ui(() -> f[0].navigateTo(s)); Thread.sleep(900);
            shot(root, s.name().toLowerCase() + ".png");
        }
        check("Orders, Menu, Tables and Dashboard open after billing", true);

        System.out.println("RESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
    static int tt(int t) { return t; }
}

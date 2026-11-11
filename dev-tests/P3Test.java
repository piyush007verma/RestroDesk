import com.restaurant.dao.*;
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

/** Phase 3 UI test: the REAL MainFrame, REAL screens, REAL DAOs and a REAL database (config/db.properties in the current folder). */
public class P3Test {
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
    static RoundedButton btn(Container c, String text) { return all(c, RoundedButton.class).stream().filter(b -> b.getText().startsWith(text)).findFirst().orElseThrow(); }
    static void shot(Component c, String file) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        ui(() -> c.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", new File(file));
    }
    static String cartTotal(Container p) { return all(p, JLabel.class).stream().filter(l -> l.isShowing() && l.getFont().getSize() == 20).map(JLabel::getText).findFirst().orElse("?"); }

    public static void main(String[] a) throws Exception {
        String shots = a[0];
        TableService tables = new TableService(new TableDaoImpl());
        int t6 = tables.getTables().stream().filter(t -> t.getNumber() == 6).findFirst().orElseThrow().getId();
        tables.assignTable(t6, "Screen Tester", "9123456780");

        MainFrame[] f = new MainFrame[1];
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        ui(() -> f[0].navigateTo(Screen.ORDERS));
        Container root = f[0].getContentPane();
        // visible tables: orders, cart, menu (in that order)
        until(() -> all(root, JTable.class).stream().filter(JTable::isShowing).count() >= 3, 4000);
        List<JTable> t = new ArrayList<>();
        ui(() -> all(root, JTable.class).stream().filter(JTable::isShowing).forEach(t::add));
        JTable orders = t.get(0), cart = t.get(1), menu = t.get(2);
        check("Orders screen opens from the sidebar and shows 3 tables", t.size() == 3);
        check("Active Orders loads from MySQL (2 orders: table 3 SERVED + table 6 PENDING)", until(() -> orders.getRowCount() == 2, 4000));
        check("menu loads from MySQL with 10 dishes (9 + Phase3 Test Dish)", until(() -> menu.getRowCount() == 10, 4000));
        check("menu table shows a Category column with text", until(() -> menu.getModel().getColumnName(1).equals("Category") && menu.getValueAt(0, 1).toString().length() > 0, 2000));

        int row6 = -1;
        for (int i = 0; i < orders.getRowCount(); i++) if (orders.getValueAt(i, 1).equals("T6")) row6 = i;
        final int r6 = row6;
        check("order for table 6 is listed with customer name and PENDING", r6 >= 0 && orders.getValueAt(r6, 2).equals("Screen Tester") && orders.getValueAt(r6, 5).equals("PENDING"));
        RoundedButton add = btn(root, "Add to Order");
        check("Add to Order disabled before anything is selected", !add.isEnabled());
        ui(() -> orders.setRowSelectionInterval(r6, r6));
        check("selecting the order shows its (empty) cart", until(() -> add != null && cart.getRowCount() == 0, 2000));

        // choose the first available dish
        int dishRow = -1;
        for (int i = 0; i < menu.getRowCount(); i++) if (menu.getValueAt(i, 3).equals("Available")) { dishRow = i; break; }
        final int dr = dishRow; String dishName = (String) menu.getValueAt(dr, 0);
        ui(() -> menu.setRowSelectionInterval(dr, dr));
        check("Add to Order enabled for an available dish + selected order", until(add::isEnabled, 2000));
        JSpinner qty = all(root, JSpinner.class).get(0);
        ui(() -> qty.setValue(2));
        ui(add::doClick);
        check("dish appears in the cart with quantity 2", until(() -> cart.getRowCount() == 1 && cart.getValueAt(0, 3).toString().equals("2"), 4000));
        check("cart row has Item, Category, Price, Qty, Subtotal columns", cart.getColumnCount() == 5 && cart.getColumnName(4).equals("Subtotal") && cart.getValueAt(0, 0).equals(dishName));
        String sub = cart.getValueAt(0, 4).toString();
        check("order total label equals the subtotal of the only line", until(() -> cartTotal(root).equals(sub), 2000));
        check("Active Orders row now shows quantity 2", until(() -> orders.getValueAt(r6, 3).toString().equals("2"), 3000));

        ui(() -> cart.setRowSelectionInterval(0, 0));
        RoundedButton plus = btn(root, "+"), minus = btn(root, "−"), remove = btn(root, "Remove");
        check("+ / - / Remove enabled once an item is selected", until(() -> plus.isEnabled() && minus.isEnabled() && remove.isEnabled(), 2000));
        ui(plus::doClick);
        check("+ makes quantity 3", until(() -> cart.getRowCount() == 1 && cart.getValueAt(0, 3).toString().equals("3"), 4000));
        ui(() -> cart.setRowSelectionInterval(0, 0));
        until(minus::isEnabled, 2000);
        ui(minus::doClick); 
        check("- makes quantity 2 again", until(() -> cart.getValueAt(0, 3).toString().equals("2"), 4000));

        // category filter
        JComboBox<?> filter = all(root, JComboBox.class).stream().filter(JComboBox::isShowing).findFirst().orElseThrow();
        ui(() -> filter.setSelectedItem("Beverages"));
        check("category filter shows only Beverages dishes", until(() -> menu.getRowCount() > 0 && menu.getRowCount() < 10 && menu.getValueAt(0, 1).equals("Beverages"), 2000));
        ui(() -> filter.setSelectedIndex(0));
        check("filter 'All categories' shows all 10 again", until(() -> menu.getRowCount() == 10, 2000));

        shot(root, shots + "/orders_with_items.png");
        RoundedButton fwd = btn(root, "Send to Kitchen");
        ui(fwd::doClick);
        check("Send to Kitchen -> PREPARING (list + button text)", until(() -> orders.getValueAt(r6, 5).equals("PREPARING") && btn(root, "Mark Ready").isShowing(), 4000));
        ui(() -> btn(root, "Mark Ready").doClick());
        check("Mark Ready -> READY", until(() -> orders.getValueAt(r6, 5).equals("READY"), 4000));
        shot(root, shots + "/orders_ready.png");

        // remove the item
        ui(() -> cart.setRowSelectionInterval(0, 0));
        until(remove::isEnabled, 2000);
        ui(remove::doClick);
        check("Remove empties the cart and total becomes zero", until(() -> cart.getRowCount() == 0 && cartTotal(root).contains("0.00"), 4000));

        // Menu + dashboard + tables still open
        ui(() -> f[0].navigateTo(Screen.MENU));
        Thread.sleep(1200);
        List<JTable> mt = new ArrayList<>();
        ui(() -> all(root, JTable.class).stream().filter(JTable::isShowing).forEach(mt::add));
        check("Menu screen: loads 10 dishes with categories", until(() -> mt.get(0).getRowCount() == 10 && mt.get(0).getValueAt(0, 1).toString().length() > 0, 3000));
        shot(root, shots + "/menu.png");
        ui(() -> f[0].navigateTo(Screen.TABLES)); Thread.sleep(1200);
        shot(root, shots + "/tables.png");
        ui(() -> f[0].navigateTo(Screen.DASHBOARD)); Thread.sleep(1200);
        shot(root, shots + "/dashboard.png");
        check("Tables and Dashboard screens opened without error", true);

        System.out.println("RESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}

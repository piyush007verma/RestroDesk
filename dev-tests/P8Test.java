import com.restaurant.ui.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * UI interaction test: sorting and searching must keep the RIGHT row selected, Alt+number shortcuts, empty-state messages,
 * Tab focus, and the tooltips. Needs the data left by P7Test A (or any database with dishes and open orders).
 */
public class P8Test {
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
    static List<JTable> tables(Container root) { return all(root, JTable.class).stream().filter(JTable::isShowing).toList(); }
    static JTextField search(Container root, String hint) { return all(root, JTextField.class).stream().filter(t -> t.isShowing() && t.getClass().getSimpleName().equals("SearchField")).findFirst().orElseThrow(); }

    public static void main(String[] a) throws Exception {
        MainFrame[] f = new MainFrame[1];
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        Container root = f[0].getContentPane();

        // ---- Alt+number shortcuts ----
        ui(() -> f[0].getRootPane().getActionMap().get("gotoMENU").actionPerformed(new ActionEvent(f[0], 0, "x")));
        check("Alt+2 shortcut opens Menu", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().equals("Menu Management")), 3000));
        KeyStroke alt3 = KeyStroke.getKeyStroke(KeyEvent.VK_3, InputEvent.ALT_DOWN_MASK);
        check("Alt+3 is bound to the Tables screen", f[0].getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(alt3) != null);

        // ---- Menu: sort, search, selection ----
        until(() -> !tables(root).isEmpty() && tables(root).get(0).getRowCount() >= 5, 4000);
        JTable menu = tables(root).get(0);
        int total = menu.getRowCount();
        JTextField box = search(root, "dish");
        ui(() -> menu.getTableHeader().getColumnModel().getColumn(2).setHeaderValue("Price"));
        ui(() -> menu.getRowSorter().toggleSortOrder(2));              // price ascending
        double first = Double.parseDouble(menu.getValueAt(0, 2).toString().replaceAll("[^0-9.]", ""));
        double last = Double.parseDouble(menu.getValueAt(total - 1, 2).toString().replaceAll("[^0-9.]", ""));
        check("clicking the Price header sorts numerically (cheapest first)", first <= last);
        ui(() -> menu.getRowSorter().toggleSortOrder(2));
        double first2 = Double.parseDouble(menu.getValueAt(0, 2).toString().replaceAll("[^0-9.]", ""));
        check("clicking again reverses the order", first2 >= last);
        String clicked = menu.getValueAt(2, 0).toString();
        ui(() -> menu.setRowSelectionInterval(2, 2));
        JTextField name = all(root, JTextField.class).stream().filter(t -> t.isShowing() && !t.getClass().getSimpleName().equals("SearchField")).findFirst().orElseThrow();
        check("selecting a row of the SORTED table loads that same dish in the form", until(() -> name.getText().equals(clicked), 3000));
        ui(() -> box.setText("chicken"));
        check("typing in the search box filters the dishes", until(() -> menu.getRowCount() >= 1 && menu.getRowCount() < total
                && menu.getValueAt(0, 0).toString().toLowerCase().contains("chicken"), 3000));
        ui(() -> box.setText("zzzz-nothing"));
        check("a search with no match shows the empty-state message", until(() -> menu.getRowCount() == 0
                && all(menu, JLabel.class).stream().anyMatch(l -> l.isVisible() && l.getText().contains("No dish matches")), 3000));
        ui(() -> box.setText(""));
        check("clearing the search shows every dish again", until(() -> menu.getRowCount() == total, 3000));

        // ---- Orders: search ----
        ui(() -> f[0].navigateTo(Screen.ORDERS));
        until(() -> tables(root).get(0).getRowCount() >= 1, 4000);
        JTable ord = tables(root).get(0);
        int ordTotal = ord.getRowCount();
        JTextField os = search(root, "orders");
        ui(() -> os.setText("zzzz-nothing"));
        check("Orders search with no match shows 0 rows and a message", until(() -> ord.getRowCount() == 0
                && all(ord, JLabel.class).stream().anyMatch(l -> l.isVisible() && l.getText().contains("No order matches")), 3000));
        ui(() -> os.setText(""));
        check("clearing the Orders search restores the list", until(() -> ord.getRowCount() == ordTotal, 3000));
        ui(() -> ord.setRowSelectionInterval(0, 0));
        check("selecting an order opens its details", until(() -> all(root, JLabel.class).stream().anyMatch(l -> l.isShowing() && l.getText().startsWith("Order #")), 3000));

        // ---- tooltips on sidebar, loading bar exists ----
        check("sidebar buttons have tooltips with the shortcut", all(root, JButton.class).stream().anyMatch(b -> b.getToolTipText() != null && b.getToolTipText().contains("Alt+1")));
        check("a loading bar exists and hides again when the work is done", all(root, JProgressBar.class).size() == 1 && until(() -> !all(root, JProgressBar.class).get(0).isVisible(), 5000));

        // ---- History sort keeps selection correct ----
        ui(() -> f[0].navigateTo(Screen.HISTORY));
        until(() -> tables(root).size() >= 2 && tables(root).get(0).getRowCount() >= 2, 4000);
        JTable hist = tables(root).get(0);
        ui(() -> hist.getRowSorter().toggleSortOrder(2));              // sort by customer
        String who = hist.getValueAt(1, 2).toString();
        ui(() -> hist.setRowSelectionInterval(1, 1));
        check("History: after sorting by customer, the clicked row's details are shown", until(() -> all(root, JLabel.class).stream()
                .anyMatch(l -> l.isShowing() && l.getText().contains(who)), 3000));
        System.out.println("RESULT P8: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}

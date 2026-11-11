import com.restaurant.dao.MenuDao;
import com.restaurant.dao.TableDao;
import com.restaurant.exception.*;
import com.restaurant.model.*;
import com.restaurant.model.MenuItem;
import com.restaurant.service.*;
import com.restaurant.ui.*;
import com.restaurant.ui.components.RoundedButton;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

public class M2Test {
    static int pass = 0, fail = 0, mark = 0;
    static final List<String[]> dialogs = Collections.synchronizedList(new ArrayList<>());
    static String outDir;

    // ---------- fake DAOs (in memory, mimic the real DAO rules) ----------
    static class FakeMenuDao implements MenuDao {
        final List<MenuItem> data = new ArrayList<>(); int next = 1;
        void seed(String n, String c, String p, boolean a) { data.add(new MenuItem(next++, n, c, new BigDecimal(p), a)); }
        public synchronized List<MenuItem> findAll() {
            List<MenuItem> l = new ArrayList<>(data);
            l.sort(Comparator.comparing(MenuItem::getCategory).thenComparing(MenuItem::getName)); return l; }
        public synchronized int insert(MenuItem i) throws InvalidInputException {
            for (MenuItem m : data) if (m.getName().equalsIgnoreCase(i.getName()))
                throw new InvalidInputException("A menu item named \"" + i.getName() + "\" already exists. Please use a different name.");
            data.add(new MenuItem(next, i.getName(), i.getCategory(), i.getPrice(), i.isAvailable())); return next++; }
        public synchronized void update(MenuItem i) throws InvalidInputException {
            for (int k = 0; k < data.size(); k++) if (data.get(k).getId() == i.getId()) { data.set(k, i); return; }
            throw new InvalidInputException("This item no longer exists."); }
        public synchronized void delete(int id) throws InvalidInputException {
            if (!data.removeIf(m -> m.getId() == id)) throw new InvalidInputException("This item no longer exists."); }
    }

    static class FakeTableDao implements TableDao {
        final Map<Integer, int[]> t = new TreeMap<>(); // id -> {number, capacity, occupied(0/1), orderId}
        final Map<Integer, String[]> orders = new LinkedHashMap<>(); // orderId -> {tableId,name,phone,status}
        int nextOrder = 1;
        void addTable(int id, int cap, boolean occ) { t.put(id, new int[]{id, cap, occ ? 1 : 0, 0}); }
        void seatDirect(int id, String name) { int[] r = t.get(id); r[2] = 1; r[3] = nextOrder; orders.put(nextOrder++, new String[]{""+id, name, null, "PENDING"}); }
        public synchronized List<TableOverview> findAllWithCustomers() {
            List<TableOverview> l = new ArrayList<>();
            for (int[] r : t.values()) { String[] o = r[3] > 0 ? orders.get(r[3]) : null;
                l.add(new TableOverview(r[0], r[0], r[1], r[2] == 1 ? TableStatus.OCCUPIED : TableStatus.AVAILABLE,
                        o != null && o[3].equals("PENDING") ? r[3] : 0, o != null ? o[1] : null, o != null ? o[2] : null)); }
            return l; }
        public synchronized int assignTable(int id, String n, String p) throws TableUnavailableException {
            int[] r = t.get(id); if (r[2] == 1) throw new TableUnavailableException("This table is not available any more.");
            r[2] = 1; r[3] = nextOrder; orders.put(nextOrder, new String[]{""+id, n, p, "PENDING"}); return nextOrder++; }
        public synchronized void releaseTable(int id) throws TableUnavailableException {
            int[] r = t.get(id); if (r[2] == 0) throw new TableUnavailableException("This table is not occupied.");
            r[2] = 0; for (String[] o : orders.values()) if (o[0].equals(""+id) && o[3].equals("PENDING")) o[3] = "CANCELLED"; r[3] = 0; }
    }

    // ---------- helpers ----------
    static void check(String name, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS  " : "FAIL  ") + name); }

    static <T> List<T> all(Container c, Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Component k : c.getComponents()) { if (type.isInstance(k)) out.add(type.cast(k)); if (k instanceof Container cc) out.addAll(all(cc, type)); }
        return out; }

    static boolean waitUntil(BooleanSupplier s, int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) {
            boolean[] r = {false}; SwingUtilities.invokeAndWait(() -> r[0] = s.getAsBoolean());
            if (r[0]) return true; Thread.sleep(40); }
        return false; }

    static void ui(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    static void settle() throws Exception { Thread.sleep(350); ui(() -> {}); }

    static RoundedButton button(Container c, String text) {
        for (RoundedButton b : all(c, RoundedButton.class)) if (b.getText().equals(text)) return b;
        throw new RuntimeException("no button " + text); }

    static List<JTextField> plainFields(Container c) {
        List<JTextField> l = new ArrayList<>();
        for (JTextField f : all(c, JTextField.class)) if (!(f.getParent() instanceof JComboBox) && !f.getClass().getSimpleName().equals("SearchField")) l.add(f);
        return l; }

    /** closes every popup dialog (answers Yes) and records its title + text. */
    static void startDialogWatcher() {
        Thread th = new Thread(() -> { while (true) {
            try { Thread.sleep(40); } catch (InterruptedException e) { return; }
            for (Window w : Window.getWindows()) if (w instanceof JDialog d && d.isVisible()) {
                List<JOptionPane> panes = all(d.getContentPane(), JOptionPane.class);
                JOptionPane p = panes.isEmpty() ? null : panes.get(0);
                if (p == null && d.getContentPane() instanceof JOptionPane jp) p = jp;
                if (p != null) { final JOptionPane fp = p; dialogs.add(new String[]{d.getTitle(), String.valueOf(fp.getMessage())});
                    SwingUtilities.invokeLater(() -> fp.setValue(JOptionPane.YES_OPTION)); }
            } } });
        th.setDaemon(true); th.start(); }

    static boolean lastDialogHas(String title, String text) {
        synchronized (dialogs) { if (dialogs.isEmpty()) return false; String[] d = dialogs.get(dialogs.size() - 1);
            return d[0].contains(title) && d[1].contains(text); } }

    static void shot(JFrame f, String file) throws Exception {
        ui(() -> { try { BufferedImage i = new BufferedImage(f.getWidth(), f.getHeight(), BufferedImage.TYPE_INT_RGB);
            f.paint(i.getGraphics()); ImageIO.write(i, "png", new File(outDir, file)); } catch (Exception e) { throw new RuntimeException(e); } }); }

    static JFrame show(JPanel p) throws Exception {
        JFrame[] f = new JFrame[1];
        ui(() -> { f[0] = new JFrame("test"); f[0].setSize(1300, 820); f[0].add(p); f[0].setVisible(true); });
        return f[0]; }

    static void fill(JTextField f, String s) throws Exception { ui(() -> f.setText(s)); }
    static void click(AbstractButton b) throws Exception { ui(b::doClick); }

    public static void main(String[] a) throws Exception {
        outDir = a[0];
        startDialogWatcher();
        menuTests();
        tableTests();
        System.out.println("\nRESULT: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }

    // ---------- MENU ----------
    static void menuTests() throws Exception {
        System.out.println("== MENU ==");
        FakeMenuDao dao = new FakeMenuDao();
        dao.seed("Paneer Tikka", "Starters", "260", true); dao.seed("Butter Chicken", "Main Course", "380", true);
        dao.seed("Dal Makhani", "Main Course", "280", true); dao.seed("Gulab Jamun", "Desserts", "120", true);
        dao.seed("Masala Chai", "Beverages", "50", false);
        MenuPanel panel = new MenuPanel(new MenuService(dao));
        JFrame f = show(panel);
        ui(panel::onShow);
        JTable table = all(panel, JTable.class).get(0);
        check("menu: loads 5 items into table", waitUntil(() -> table.getRowCount() == 5, 3000));
        List<JTextField> fields = plainFields(panel); JTextField name = fields.get(0), price = fields.get(1);
        JComboBox<?> cat = all(panel, JComboBox.class).get(1);
        check("menu: category list has defaults + used", cat.getItemCount() >= 5);
        RoundedButton add = button(panel, "Add Item"), upd = button(panel, "Update"), del = button(panel, "Delete");
        check("menu: add enabled, update/delete disabled initially", add.isEnabled() && !upd.isEnabled() && !del.isEnabled());

        // validation
        mark = dialogs.size();
        click(add); check("menu: empty name rejected", waitUntil(() -> dialogs.size() > mark, 3000) && lastDialogHas("check your input", "name"));
        fill(name, "Veg Roll"); mark = dialogs.size();
        click(add); check("menu: empty category rejected", waitUntil(() -> dialogs.size() > mark, 3000) && lastDialogHas("check your input", "category"));
        ui(() -> cat.setSelectedItem("Starters")); mark = dialogs.size();
        click(add); check("menu: empty price rejected", waitUntil(() -> dialogs.size() > mark, 3000) && lastDialogHas("check your input", "price"));
        for (String[] bad : new String[][]{{"abc", "must be a number"}, {"-5", "greater than 0"}, {"0", "greater than 0"}, {"10.999", "2 digits"}}) {
            fill(price, bad[0]); mark = dialogs.size(); click(add);
            check("menu: bad price '" + bad[0] + "' rejected", waitUntil(() -> dialogs.size() > dialogs.size() - 1, 100) && waitUntil(() -> lastDialogHasAfter(bad[1]), 3000));
            settle(); }
        check("menu: nothing saved after invalid input", dao.data.size() == 5);

        // add
        fill(price, "149.5"); click(add);
        check("menu: valid add saves item", waitUntil(() -> dao.data.size() == 6, 3000));
        check("menu: table refreshes to 6 rows", waitUntil(() -> table.getRowCount() == 6, 3000));
        check("menu: saved price is 149.50", dao.data.get(5).getPrice().toPlainString().equals("149.50"));
        check("menu: form cleared after add", name.getText().isEmpty() && price.getText().isEmpty());

        // duplicate name (case-insensitive)
        fill(name, "veg roll"); fill(price, "100"); ui(() -> cat.setSelectedItem("Starters")); mark = dialogs.size(); click(add);
        check("menu: duplicate name gives friendly message", waitUntil(() -> dialogs.size() > dialogs.size() - 1 && lastDialogHasAfter("already exists"), 3000));
        settle();

        // select + edit
        ui(() -> table.setRowSelectionInterval(0, 0));
        check("menu: selecting a row fills the form", waitUntil(() -> !name.getText().isEmpty() && upd.isEnabled() && del.isEnabled() && !add.isEnabled(), 2000));
        String editedName = name.getText();
        fill(price, "999"); click(upd);
        check("menu: update saves new price", waitUntil(() -> dao.data.stream().anyMatch(m -> m.getName().equals(editedName) && m.getPrice().compareTo(new BigDecimal("999")) == 0), 3000));
        settle();
        check("menu: row stays selected after update", table.getSelectedRow() >= 0);
        JCheckBox avail = all(panel, JCheckBox.class).get(0);
        ui(() -> avail.setSelected(false)); click(upd);
        check("menu: availability can be switched off", waitUntil(() -> dao.data.stream().anyMatch(m -> m.getName().equals(editedName) && !m.isAvailable()), 3000));
        settle();
        shot(f, "menu_selected.png");

        // delete (watcher answers YES to the confirm dialog)
        mark = dialogs.size(); click(del);
        check("menu: delete asks for confirmation", waitUntil(() -> dialogs.size() > dialogs.size() - 1 && lastDialogHasAfter("Delete \"" + editedName + "\""), 3000));
        check("menu: delete removes the item", waitUntil(() -> dao.data.size() == 5 && dao.data.stream().noneMatch(m -> m.getName().equals(editedName)), 3000));
        check("menu: table refreshes after delete", waitUntil(() -> table.getRowCount() == 5, 3000));
        check("menu: back to add mode after delete", waitUntil(() -> add.isEnabled() && !del.isEnabled(), 2000));
        ui(() -> f.dispose());
    }

    static boolean lastDialogHasAfter(String text) {
        synchronized (dialogs) { for (int i = dialogs.size() - 1; i >= Math.max(0, dialogs.size() - 3); i--) if (dialogs.get(i)[1].contains(text)) return true; return false; } }

    // ---------- TABLES ----------
    static void tableTests() throws Exception {
        System.out.println("== TABLES ==");
        FakeTableDao dao = new FakeTableDao();
        int[] caps = {2, 2, 4, 4, 4, 6, 6, 8, 2};
        for (int i = 0; i < caps.length; i++) dao.addTable(i + 1, caps[i], false);
        dao.seatDirect(1, "Rohan Verma"); dao.seatDirect(3, "Aarav Mehta"); dao.seatDirect(6, "Neha Kapoor");
        TableService service = new TableService(dao);
        TablesPanel panel = new TablesPanel(service);
        JFrame f = show(panel);
        ui(panel::onShow);
        check("tables: loads 9 table cards", waitUntil(() -> cards(panel).size() == 9, 3000));
        check("tables: counts shown (6 available / 3 occupied)", waitUntil(() -> hasText(panel, "Available 6") && hasText(panel, "Occupied 3"), 2000));
        RoundedButton assign = button(panel, "Assign Table"), release = button(panel, "Release Table");
        check("tables: both buttons disabled with nothing selected", !assign.isEnabled() && !release.isEnabled());
        List<JTextField> fields = plainFields(panel); JTextField name = fields.get(0), phone = fields.get(1);

        // select free table 2
        clickCard(panel, 2);
        check("tables: free table enables Assign only", waitUntil(() -> assign.isEnabled() && !release.isEnabled() && name.isEnabled(), 2000));
        check("tables: detail shows Table 2", hasText(panel, "Table 2"));

        // validation
        mark = dialogs.size(); click(assign);
        check("tables: empty name rejected", waitUntil(() -> dialogs.size() > mark, 3000) && lastDialogHas("check your input", "name"));
        settle();
        for (String[] bad : new String[][]{{"R2D2", "only letters"}, {"A", "at least 2"}}) {
            fill(name, bad[0]); click(assign);
            check("tables: bad name '" + bad[0] + "' rejected", waitUntil(() -> lastDialogHasAfter(bad[1]), 3000)); settle(); }
        fill(name, "Rahul Sharma"); fill(phone, "12ab");
        click(assign); check("tables: bad phone rejected", waitUntil(() -> lastDialogHasAfter("phone number"), 3000)); settle();
        check("tables: table still free after invalid input", dao.t.get(2)[2] == 0 && dao.orders.size() == 3);

        // valid assign (phone with space and hyphen is cleaned)
        fill(phone, "98765-43210"); click(assign);
        check("tables: assign marks table OCCUPIED", waitUntil(() -> dao.t.get(2)[2] == 1, 3000));
        check("tables: assign creates exactly one PENDING order", waitUntil(() -> dao.orders.size() == 4, 3000)
                && dao.orders.get(4)[3].equals("PENDING") && dao.orders.get(4)[1].equals("Rahul Sharma") && "9876543210".equals(dao.orders.get(4)[2]));
        check("tables: counts refresh (5 available / 4 occupied)", waitUntil(() -> hasText(panel, "Available 5") && hasText(panel, "Occupied 4"), 3000));
        check("tables: after assign Release enabled, Assign disabled", waitUntil(() -> release.isEnabled() && !assign.isEnabled(), 3000));
        check("tables: detail shows customer and open order", waitUntil(() -> name.getText().equals("Rahul Sharma") && hasText(panel, "Open order #4"), 3000));

        // duplicate assignment: someone else takes table 4 after our screen loaded it
        clickCard(panel, 4);
        check("tables: select free table 4", waitUntil(() -> assign.isEnabled(), 2000));
        dao.seatDirect(4, "Other Cashier Customer"); // simulates another computer
        fill(name, "Late Customer"); int before = dao.orders.size(); mark = dialogs.size();
        click(assign);
        check("tables: duplicate assignment is refused with friendly message", waitUntil(() -> dialogs.size() > mark, 3000) && lastDialogHas("not available", "not available"));
        check("tables: no extra order created for the refused assignment", dao.orders.size() == before);
        check("tables: list refreshed - table 4 now shown occupied", waitUntil(() -> release.isEnabled() && !assign.isEnabled(), 3000));
        settle();
        shot(f, "tables_selected.png");

        // release (watcher answers YES)
        clickCard(panel, 2);
        check("tables: select occupied table 2", waitUntil(() -> release.isEnabled(), 2000));
        mark = dialogs.size(); click(release);
        check("tables: release asks for confirmation", waitUntil(() -> dialogs.size() > dialogs.size() - 1 && lastDialogHasAfter("Release Table 2"), 3000));
        check("tables: release frees the table", waitUntil(() -> dao.t.get(2)[2] == 0, 3000));
        check("tables: release cancels its open order", dao.orders.get(4)[3].equals("CANCELLED"));
        check("tables: display refreshed after release", waitUntil(() -> hasText(panel, "Available 5"), 3000));
        check("tables: freed table can be assigned again", waitUntil(() -> assign.isEnabled() && !release.isEnabled(), 3000));
        ui(() -> f.dispose());

        // service rules
        System.out.println("== SERVICE ==");
        try { service.assignTable(0, "Asha", null); check("service: tableId 0 rejected", false); } catch (InvalidInputException e) { check("service: tableId 0 rejected", true); }
        try { service.assignTable(5, "Anne-Marie O'Neil Jr.", "+919876543210"); check("service: name with - ' . and +91 phone accepted", true); } catch (Exception e) { check("service: name with - ' . and +91 phone accepted: " + e, false); }
        try { service.assignTable(7, "आरव शर्मा", ""); check("service: Hindi name accepted", true); } catch (Exception e) { check("service: Hindi name accepted: " + e, false); }
        try { service.assignTable(8, "x".repeat(101), null); check("service: 101-char name rejected", false); } catch (InvalidInputException e) { check("service: 101-char name rejected", true); }
        var counts = service.countByStatus(dao.findAllWithCustomers());
        check("service: countByStatus sums to 9", counts.get(TableStatus.AVAILABLE) + counts.get(TableStatus.OCCUPIED) == 9);
        MenuService ms = new MenuService(new FakeMenuDao());
        try { ms.addItem("Tea", "Beverages", "1E+3", true); check("service: price 1E+3 -> 1000.00", true); } catch (Exception e) { check("service: price 1E+3: " + e, false); }
        try { ms.addItem("Tea2", "Beverages", "99999999.99", true); check("service: max price accepted", true); } catch (Exception e) { check("service: max price: " + e, false); }
        try { ms.addItem("Tea3", "Beverages", "100000000", true); check("service: price over max rejected", false); } catch (InvalidInputException e) { check("service: price over max rejected", true); }
        try { ms.addItem("n".repeat(101), "Beverages", "5", true); check("service: 101-char item name rejected", false); } catch (InvalidInputException e) { check("service: 101-char item name rejected", true); }
        try { ms.deleteItem(0); check("service: delete without selection rejected", false); } catch (InvalidInputException e) { check("service: delete without selection rejected", true); }
    }

    static List<JPanel> cards(Container c) { List<JPanel> l = new ArrayList<>();
        for (JPanel p : all(c, JPanel.class)) if (p.getClass().getSimpleName().equals("TableCard")) l.add(p); return l; }
    static void clickCard(Container c, int tableNumber) throws Exception {
        for (JPanel p : cards(c)) for (JLabel lb : all(p, JLabel.class)) if (lb.getText().equals("Table " + tableNumber)) {
            ui(() -> { for (var ml : p.getMouseListeners()) ml.mouseClicked(new MouseEvent(p, MouseEvent.MOUSE_CLICKED, 0, 0, 5, 5, 1, false)); }); settle(); return; }
        throw new RuntimeException("no card " + tableNumber); }
    static boolean hasText(Container c, String text) { for (JLabel l : all(c, JLabel.class)) if (text.equals(l.getText())) return true; return false; }
}

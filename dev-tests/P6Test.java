import com.restaurant.ui.*;
import com.restaurant.ui.components.RoundedButton;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * Phase 6 UI test: the REAL MainFrame and the REAL Order History screen with the Clear Order History button.
 * Run it AFTER ClearHistoryItTest A (6 orders, 4 bills) and after un-flagging the orders (see dev-tests/db-integration/README.md):
 * UPDATE orders SET history_cleared_at = NULL;   (test database only)
 * It presses the real button and answers the real dialogs: first Cancel, then confirm.
 */
public class P6Test {
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
    static void shot(Component c, String file) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        ui(() -> c.paint(img.getGraphics()));
        javax.imageio.ImageIO.write(img, "png", new File(file));
    }
    /** Waits for a dialog, remembers its text and presses the named button. Returns the dialog text (or null). */
    static String answer(String button, String shotFile) throws Exception {
        long end = System.currentTimeMillis() + 8000;
        while (System.currentTimeMillis() < end) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog d && d.isShowing()) {
                    JOptionPane pane = all(d.getContentPane(), JOptionPane.class).stream().findFirst().orElse(null);
                    List<JButton> bs = all(d.getContentPane(), JButton.class);
                    Optional<JButton> b = bs.stream().filter(x -> x.getText().equals(button)).findFirst();
                    if (b.isPresent()) {
                        String text = String.valueOf(pane == null ? "" : pane.getMessage());
                        if (shotFile != null) { BufferedImage img = new BufferedImage(d.getWidth(), d.getHeight(), BufferedImage.TYPE_INT_RGB); ui(() -> d.paint(img.getGraphics())); javax.imageio.ImageIO.write(img, "png", new File(shotFile)); }
                        // default button check for the confirmation dialog
                        AtomicReference<String> def = new AtomicReference<>("");
                        ui(() -> { JButton db = d.getRootPane().getDefaultButton(); def.set(db == null ? "" : db.getText()); });
                        lastDefault = def.get();
                        ui(b.get()::doClick);
                        return text;
                    }
                }
            }
            Thread.sleep(50);
        }
        return null;
    }
    static String lastDefault = "";

    public static void main(String[] a) throws Exception {
        String shots = a[0];
        MainFrame[] f = new MainFrame[1];
        ui(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        Container root = f[0].getContentPane();
        ui(() -> f[0].navigateTo(Screen.HISTORY));
        JTable[] list = new JTable[1];
        until(() -> { var t = all(root, JTable.class).stream().filter(JTable::isShowing).toList(); if (t.size() >= 2) { list[0] = t.get(0); return true; } return false; }, 4000);
        check("History lists all 6 orders", until(() -> list[0].getRowCount() == 6, 5000));
        RoundedButton clear = btn(root, "Clear Order History");
        check("the Clear Order History button is visible and enabled", clear.isShowing() && clear.isEnabled());
        shot(root, shots + "/p6_history_before.png");

        // ---- Cancel ----
        ui(clear::doClick);
        String text = answer("Cancel", shots + "/p6_confirm_dialog.png");
        check("a confirmation dialog appears with Clear History / Cancel", text != null);
        check("the dialog says how many orders (5) and that nothing is deleted", text != null && text.contains("5 finished") && text.contains("Nothing is deleted"));
        check("Cancel is the default button (Enter does not clear)", lastDefault.equals("Cancel"));
        Thread.sleep(600);
        check("after Cancel the list is unchanged (6 rows) and the button works again", list[0].getRowCount() == 6 && until(clear::isEnabled, 3000));

        // ---- Confirm ----
        ui(clear::doClick);
        String t2 = answer("Clear History", null);
        check("confirm dialog shown again", t2 != null);
        String done = answer("OK", shots + "/p6_success_dialog.png");
        check("a success message appears (5 orders removed from the list, bills untouched)", done != null && done.contains("5 orders were removed") && done.contains("not changed"));
        check("the list refreshes and shows only the open order", until(() -> list[0].getRowCount() == 1, 5000));
        shot(root, shots + "/p6_history_after.png");

        // ---- nothing to clear ----
        until(clear::isEnabled, 3000);
        ui(clear::doClick);
        String none = answer("OK", null);
        check("clearing again says there is nothing to clear", none != null && none.contains("no finished"));

        // ---- show cleared ----
        JCheckBox cb = all(root, JCheckBox.class).stream().filter(c -> c.getText().equals("Show cleared orders")).findFirst().orElseThrow();
        ui(cb::doClick);
        check("'Show cleared orders' brings back all 6 rows", until(() -> list[0].getRowCount() == 6, 5000));
        shot(root, shots + "/p6_history_showcleared.png");
        System.out.println("RESULT P6: " + pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}

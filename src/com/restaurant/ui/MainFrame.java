package com.restaurant.ui;

import com.restaurant.dao.BillDaoImpl;
import com.restaurant.dao.DashboardDaoImpl;
import com.restaurant.dao.HistoryDaoImpl;
import com.restaurant.dao.ReportsDaoImpl;
import com.restaurant.dao.MenuDaoImpl;
import com.restaurant.dao.OrderDaoImpl;
import com.restaurant.dao.TableDaoImpl;
import com.restaurant.service.BillingService;
import com.restaurant.service.DashboardService;
import com.restaurant.service.HistoryService;
import com.restaurant.service.ReportsService;
import com.restaurant.service.MenuService;
import com.restaurant.service.OrderService;
import com.restaurant.service.TableService;
import com.restaurant.ui.components.SidebarButton;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JProgressBar;
import javax.swing.KeyStroke;
import javax.swing.AbstractAction;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * The main application window: dark navy sidebar on the left, and a "card" area
 * on the right that shows one screen at a time.
 *
 * INTEGRATION POINT: registerScreens() is the only place where screens are
 * plugged into the app. When a member finishes a screen, they change just
 * their own line there.
 */
public class MainFrame extends JFrame {

    private static final int SIDEBAR_WIDTH = 250;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel content = new JPanel(cardLayout);
    private final Map<Screen, ScreenPanel> screens = new EnumMap<>(Screen.class);
    private final Map<Screen, SidebarButton> buttons = new EnumMap<>(Screen.class);

    public MainFrame() {
        super("RestoDesk - Restaurant Billing and Table Management");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1320, 820);
        setMinimumSize(new Dimension(1100, 680));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        registerScreens();

        add(buildSidebar(), BorderLayout.WEST);
        content.setBackground(Theme.BACKGROUND);
        for (Screen s : Screen.values()) {
            content.add(screens.get(s), s.name());
        }
        // thin loading bar above the screens: visible only while a database task runs in the background
        JProgressBar loading = new JProgressBar();
        loading.setIndeterminate(true);
        loading.setBorderPainted(false);
        loading.setForeground(Theme.ACCENT);
        loading.setBackground(Theme.BACKGROUND);
        loading.setPreferredSize(new Dimension(0, 4));
        loading.setVisible(false);
        Background.setBusyListener(loading::setVisible);
        JPanel center = new JPanel(new BorderLayout());
        center.add(loading, BorderLayout.NORTH);
        center.add(content, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        installShortcuts();

        navigateTo(Screen.DASHBOARD);
    }

    /** Plug every screen into the app. One line per screen, one owner per line. */
    private void registerScreens() {
        screens.put(Screen.DASHBOARD, new DashboardPanel(new DashboardService(new DashboardDaoImpl()), this::navigateTo, new ReportsService(new ReportsDaoImpl()))); // Member 4
        screens.put(Screen.MENU, new MenuPanel(new MenuService(new MenuDaoImpl())));       // Member 1
        screens.put(Screen.TABLES, new TablesPanel(new TableService(new TableDaoImpl()))); // Member 1
        screens.put(Screen.ORDERS, new OrdersPanel(new OrderService(new OrderDaoImpl()), new MenuService(new MenuDaoImpl())));   // Member 2
        BillingService billingService = new BillingService(new BillDaoImpl(), new OrderDaoImpl());
        OrderDaoImpl orderDao = new OrderDaoImpl();
        screens.put(Screen.BILLING, new BillingPanel(billingService)); // Member 3
        screens.put(Screen.HISTORY, new HistoryPanel(new HistoryService(new HistoryDaoImpl(), billingService, orderDao))); // Member 4
        screens.put(Screen.REPORTS, new ReportsPanel(new ReportsService(new ReportsDaoImpl()))); // Member 4
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(Theme.NAVY);
        sidebar.setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(28, 14, 20, 14));

        JLabel brand = new JLabel("RestoDesk");
        brand.setFont(Theme.font(java.awt.Font.BOLD, 24));
        brand.setForeground(Theme.ACCENT);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

        JLabel tagline = new JLabel("Billing and Table Management");
        tagline.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        tagline.setForeground(Theme.SIDEBAR_TEXT);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagline.setBorder(BorderFactory.createEmptyBorder(2, 10, 0, 0));

        sidebar.add(brand);
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(32));

        for (Screen s : Screen.values()) {
            SidebarButton button = new SidebarButton(s.getLabel(), s.getBadge());
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> navigateTo(s)); // lambda: runs when clicked
            button.setToolTipText(s.getLabel() + "  (Alt+" + (s.ordinal() + 1) + ")");
            buttons.put(s, button);
            sidebar.add(button);
            sidebar.add(Box.createVerticalStrut(4));
        }

        sidebar.add(Box.createVerticalGlue());

        JLabel version = new JLabel("v1.0  -  Final");
        version.setFont(Theme.font(java.awt.Font.PLAIN, 11));
        version.setForeground(Theme.SIDEBAR_TEXT);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        version.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        sidebar.add(version);
        return sidebar;
    }

    /** Alt+1 ... Alt+7 open the screens in sidebar order (keyboard-friendly navigation). */
    private void installShortcuts() {
        javax.swing.JRootPane root = getRootPane();
        for (Screen s : Screen.values()) {
            String key = "goto" + s.name();
            root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyStroke.getKeyStroke('1' + s.ordinal(), InputEvent.ALT_DOWN_MASK).getKeyCode(), InputEvent.ALT_DOWN_MASK), key);
            root.getActionMap().put(key, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    navigateTo(s);
                }
            });
        }
    }

    /** Switch to a screen, highlight its sidebar button and let it refresh. */
    public void navigateTo(Screen target) {
        cardLayout.show(content, target.name());
        buttons.forEach((screen, button) -> button.setActive(screen == target));
        screens.get(target).onShow();
    }
}

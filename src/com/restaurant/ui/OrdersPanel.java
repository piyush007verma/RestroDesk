package com.restaurant.ui;

import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MenuItem;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.TableOverview;
import com.restaurant.service.MenuService;
import com.restaurant.service.OrderService;
import com.restaurant.ui.components.DataTableStyle;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.SearchField;
import com.restaurant.ui.components.StatusBadge;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Order Management screen. OWNER: Member 2.
 * <pre>
 *  left, top    Active Orders: every order that is not paid or cancelled (click one to work on it)
 *  left, bottom the cart of the selected order: items, quantities, total, and the kitchen status buttons
 *  right        the menu: pick a dish (filter by category) and add it to the selected order
 * </pre>
 * The order itself is created when a table is assigned (Tables screen). This screen only adds food to that order
 * and moves it through PENDING -> PREPARING -> READY -> SERVED. No SQL and no rules here: clicks go to
 * {@link OrderService}, and all database work runs through {@link Background}.
 */
public class OrdersPanel extends ScreenPanel {

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ENGLISH);
    private static final String ALL_CATEGORIES = "All categories";

    private final OrderService orderService;
    private final MenuService menuService;

    // what is on screen
    private final List<OrderSummary> orders = new ArrayList<>();
    private final List<OrderLine> lines = new ArrayList<>();
    private final List<MenuItem> menu = new ArrayList<>();
    private final List<MenuItem> menuShown = new ArrayList<>();
    private int selectedOrderId = 0;
    private int selectedLineItemId = 0;
    private int selectedDishId = 0;
    private boolean busy = false;
    /** true while the program itself (not the user) changes a table selection or the filter box. */
    private boolean filling = false;

    // active orders
    private final DefaultTableModel ordersModel = readOnlyModel("ID", "Table", "Customer", "Qty", "Total", "Status", "Placed");
    private final JTable ordersTable = new JTable(ordersModel);
    private final JLabel ordersCount = new JLabel();
    private final JLabel ordersNotice = new JLabel();
    private final RoundedButton seatedButton = new RoundedButton("Seated table", Theme.TEXT_MUTED);

    // cart
    private final DefaultTableModel cartModel = readOnlyModel("Item", "Category", "Price", "Qty", "Subtotal");
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel cartTitle = new JLabel("No order selected");
    private final JLabel cartInfo = new JLabel("Click an order in the list above");
    private final StatusBadge cartBadge = new StatusBadge();
    private final JLabel cartHint = new JLabel(" ");
    private final JLabel totalLabel = new JLabel(CURRENCY.format(0));
    private final RoundedButton plusButton = new RoundedButton("+", Theme.INFO);
    private final RoundedButton minusButton = new RoundedButton("−", Theme.INFO);
    private final RoundedButton removeButton = new RoundedButton("Remove", Theme.DANGER);
    private final RoundedButton backButton = new RoundedButton("Back", Theme.TEXT_MUTED);
    private final RoundedButton forwardButton = new RoundedButton("Send to Kitchen", Theme.ACCENT);

    // menu
    private final DefaultTableModel menuModel = readOnlyModel("Dish", "Category", "Price", "Status");
    private final JTable menuTable = new JTable(menuModel);
    private final JComboBox<String> categoryFilter = new JComboBox<>();
    private final JLabel menuNotice = new JLabel();
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, OrderLine.MAX_QUANTITY, 1));
    private final RoundedButton addButton = new RoundedButton("Add to Order", Theme.ACCENT);

    public OrdersPanel(OrderService orderService, MenuService menuService) {
        super("Order Management", "Add food to an order, change quantities and follow it through the kitchen");
        this.orderService = orderService;
        this.menuService = menuService;

        JPanel ordersCard = buildOrdersCard();
        JPanel cartCard = buildCartCard();
        JPanel left = new JPanel(new BorderLayout(0, 18));
        left.setOpaque(false);
        left.add(ordersCard, BorderLayout.NORTH);
        left.add(cartCard, BorderLayout.CENTER);
        // the order list gets about 40 percent of the height, the cart the rest
        left.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ordersCard.setPreferredSize(new Dimension(10, Math.max(190, (int) (left.getHeight() * 0.40))));
                left.revalidate();
            }
        });
        ordersCard.setPreferredSize(new Dimension(10, 250));

        body.setLayout(new BorderLayout(18, 0));
        body.add(left, BorderLayout.CENTER);
        body.add(buildMenuCard(), BorderLayout.EAST);

        wireActions();
        showSelectedOrder();
        applyButtonState();
    }

    /** Called every time the user opens this screen: show the latest orders and the latest menu. */
    @Override
    public void onShow() {
        refresh();
        refreshMenu();
    }

    // ---------- building the screen ----------

    private JPanel buildOrdersCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));

        JLabel title = cardTitle("Active Orders");
        ordersCount.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        ordersCount.setForeground(Theme.TEXT_MUTED);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(new SearchField("Search orders...", 10, text -> DataTableStyle.filterRows(ordersTable, text)));
        right.add(ordersCount);
        right.add(seatedButton);
        seatedButton.setToolTipText("For a table that has customers but no order (rare). Normal orders start on the Tables screen.");

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(right, BorderLayout.EAST);

        styleNotice(ordersNotice);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(ordersNotice, BorderLayout.SOUTH);

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(ordersTable, 3, 4), BorderLayout.CENTER);
        DataTableStyle.enableSorting(ordersTable);
        DataTableStyle.emptyState(ordersTable, "No open orders. Seat a customer on the Tables screen to start one.", "No order matches your search.");
        ordersTable.getColumnModel().getColumn(5).setCellRenderer(new DataTableStyle.BadgeRenderer(Theme::statusColor));
        setWidths(ordersTable, 34, 44, 112, 36, 88, 92, 92);
        return card;
    }

    private JPanel buildCartCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));

        cartTitle.setFont(Theme.font(java.awt.Font.BOLD, 16));
        cartTitle.setForeground(Theme.TEXT_DARK);
        cartInfo.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        cartInfo.setForeground(Theme.TEXT_MUTED);
        cartHint.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        cartHint.setForeground(Theme.WARNING);
        cartBadge.setStatus("-", Theme.TEXT_MUTED);

        JPanel texts = new JPanel(new BorderLayout(0, 2));
        texts.setOpaque(false);
        texts.add(cartTitle, BorderLayout.NORTH);
        texts.add(cartInfo, BorderLayout.CENTER);
        texts.add(cartHint, BorderLayout.SOUTH);
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeHolder.setOpaque(false);
        badgeHolder.add(cartBadge);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(texts, BorderLayout.CENTER);
        header.add(badgeHolder, BorderLayout.EAST);

        // footer: total, then the quantity buttons, then the kitchen buttons
        JLabel totalText = new JLabel("Order total");
        totalText.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        totalText.setForeground(Theme.TEXT_MUTED);
        totalLabel.setFont(Theme.font(java.awt.Font.BOLD, 20));
        totalLabel.setForeground(Theme.TEXT_DARK);
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        totalRow.add(totalText, BorderLayout.WEST);
        totalRow.add(totalLabel, BorderLayout.EAST);

        JPanel quantityRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quantityRow.setOpaque(false);
        quantityRow.add(plusButton);
        quantityRow.add(minusButton);
        quantityRow.add(removeButton);
        JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusRow.setOpaque(false);
        statusRow.add(forwardButton);
        statusRow.add(backButton);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(left(totalRow));
        footer.add(Box.createVerticalStrut(8));
        footer.add(left(quantityRow));
        footer.add(Box.createVerticalStrut(6));
        footer.add(left(statusRow));

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(cartTable, 2, 3, 4), BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        setWidths(cartTable, 150, 100, 80, 50, 90);
        return card;
    }

    private JPanel buildMenuCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 10));
        card.setPreferredSize(new Dimension(440, 0));

        categoryFilter.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        categoryFilter.setBackground(java.awt.Color.WHITE);
        categoryFilter.setToolTipText("Show only one category");
        JPanel titleRow = new JPanel(new BorderLayout(10, 0));
        titleRow.setOpaque(false);
        titleRow.add(cardTitle("Menu"), BorderLayout.WEST);
        titleRow.add(categoryFilter, BorderLayout.EAST);

        styleNotice(menuNotice);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(menuNotice, BorderLayout.SOUTH);

        JLabel qtyText = new JLabel("Quantity");
        qtyText.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        qtyText.setForeground(Theme.TEXT_MUTED);
        quantitySpinner.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        quantitySpinner.setPreferredSize(new Dimension(70, 36));
        JLabel tip = new JLabel("Tip: double-click a dish to add it.");
        tip.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        tip.setForeground(Theme.TEXT_MUTED);
        JPanel addRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addRow.setOpaque(false);
        addRow.add(qtyText);
        addRow.add(quantitySpinner);
        addRow.add(addButton);
        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(left(addRow));
        footer.add(Box.createVerticalStrut(6));
        footer.add(left(tip));

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(menuTable, 2), BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        menuTable.getColumnModel().getColumn(3).setCellRenderer(new DataTableStyle.BadgeRenderer(
                text -> text.equals("Available") ? Theme.SUCCESS : Theme.DANGER));
        setWidths(menuTable, 118, 96, 92, 90);
        return card;
    }

    private static RoundedPanel card() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        return card;
    }

    private static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.BOLD, 16));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static void styleNotice(JLabel label) {
        label.setFont(Theme.font(java.awt.Font.BOLD, 12));
        label.setForeground(Theme.WARNING);
        label.setVisible(false);
    }

    private static JPanel left(JPanel p) {
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JLabel left(JLabel l) {
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static void setWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    // ---------- connecting clicks to actions ----------

    private void wireActions() {
        ordersTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = DataTableStyle.selectedModelRow(ordersTable);
            if (row >= 0 && row < orders.size() && orders.get(row).getOrderId() != selectedOrderId) {
                selectedOrderId = orders.get(row).getOrderId();
                selectedLineItemId = 0;
                lines.clear();
                fillCart();
                showSelectedOrder();
                applyButtonState();
                refresh(); // load the food items of this order
            }
        });
        cartTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = cartTable.getSelectedRow();
            selectedLineItemId = (row >= 0 && row < lines.size()) ? lines.get(row).getItemId() : 0;
            applyButtonState();
        });
        menuTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = menuTable.getSelectedRow();
            selectedDishId = (row >= 0 && row < menuShown.size()) ? menuShown.get(row).getId() : 0;
            applyButtonState();
        });
        menuTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    addButton.doClick(); // does nothing while the button is disabled
                }
            }
        });
        categoryFilter.addActionListener(e -> {
            if (!filling) {
                showMenuFilter();
            }
        });

        addButton.addActionListener(e -> onAdd());
        plusButton.addActionListener(e -> onLineAction(1));
        minusButton.addActionListener(e -> onLineAction(-1));
        removeButton.addActionListener(e -> onLineAction(0));
        forwardButton.addActionListener(e -> onMove(true));
        backButton.addActionListener(e -> onMove(false));
        seatedButton.addActionListener(e -> onSeatedTable());
    }

    // ---------- loading data ----------

    /** Everything one refresh brings back, loaded together so the screen is never half old and half new. */
    private record Snapshot(List<OrderSummary> orders, List<OrderLine> lines, int orderId) {
    }

    /** Reloads the active orders and the food items of the selected order. */
    private void refresh() {
        int wanted = selectedOrderId;
        Background.run(() -> {
            List<OrderSummary> loaded = orderService.getActiveOrders();
            boolean stillOpen = loaded.stream().anyMatch(o -> o.getOrderId() == wanted);
            return new Snapshot(loaded, stillOpen ? orderService.getLines(wanted) : List.of(), stillOpen ? wanted : 0);
        }, this::render, error -> {
            orders.clear();
            lines.clear();
            fillOrders();
            fillCart();
            ordersNotice.setText(Messages.notice(error));
            ordersNotice.setToolTipText(error.getMessage());
            ordersNotice.setVisible(true);
            showSelectedOrder();
            applyButtonState();
        });
    }

    private void render(Snapshot snapshot) {
        ordersNotice.setVisible(false);
        if (snapshot.orderId() != selectedOrderId && selectedOrderId != 0 && snapshot.orderId() != 0) {
            // the user clicked another order while this was loading: show the orders, then load again
            orders.clear();
            orders.addAll(snapshot.orders());
            fillOrders();
            refresh();
            return;
        }
        orders.clear();
        orders.addAll(snapshot.orders());
        if (snapshot.orderId() == 0) {
            selectedOrderId = 0; // the order was paid or cancelled elsewhere, or nothing was selected
        }
        lines.clear();
        lines.addAll(snapshot.lines());
        fillOrders();
        fillCart();
        showSelectedOrder();
        applyButtonState();
    }

    private void refreshMenu() {
        Background.run(menuService::getAllItems, loaded -> {
            menuNotice.setVisible(false);
            menu.clear();
            menu.addAll(loaded);
            filling = true;
            Object chosen = categoryFilter.getSelectedItem();
            categoryFilter.removeAllItems();
            categoryFilter.addItem(ALL_CATEGORIES);
            menuService.usedCategories(menu).forEach(categoryFilter::addItem);
            categoryFilter.setSelectedItem(chosen == null ? ALL_CATEGORIES : chosen);
            if (categoryFilter.getSelectedItem() == null) {
                categoryFilter.setSelectedItem(ALL_CATEGORIES); // that category no longer exists
            }
            filling = false;
            showMenuFilter();
        }, error -> {
            menu.clear();
            menuShown.clear();
            menuModel.setRowCount(0);
            menuNotice.setText(Messages.notice(error));
            menuNotice.setToolTipText(error.getMessage());
            menuNotice.setVisible(true);
            applyButtonState();
        });
    }

    // ---------- drawing data into the screen ----------

    private void fillOrders() {
        filling = true;
        ordersModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < orders.size(); i++) {
            OrderSummary o = orders.get(i);
            ordersModel.addRow(new Object[]{
                    "#" + o.getOrderId(),
                    "T" + o.getTableNumber(),
                    o.getCustomerName(),
                    o.getItemCount(),
                    CURRENCY.format(o.getTotal()),
                    o.getStatus().name(),
                    WHEN.format(o.getCreatedAt())});
            if (o.getOrderId() == selectedOrderId) {
                select = i;
            }
        }
        if (select >= 0) {
            DataTableStyle.selectModelRow(ordersTable, select);
        } else {
            ordersTable.clearSelection();
        }
        filling = false;
        ordersCount.setText(orders.size() == 1 ? "1 order" : orders.size() + " orders");
    }

    private void fillCart() {
        filling = true;
        cartModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < lines.size(); i++) {
            OrderLine l = lines.get(i);
            cartModel.addRow(new Object[]{
                    l.getName(),
                    l.getCategory(),
                    CURRENCY.format(l.getUnitPrice()),
                    l.getQuantity(),
                    CURRENCY.format(l.getSubtotal())});
            if (l.getItemId() == selectedLineItemId) {
                select = i;
            }
        }
        if (select >= 0) {
            cartTable.setRowSelectionInterval(select, select);
        } else {
            cartTable.clearSelection();
            selectedLineItemId = 0;
        }
        filling = false;
        totalLabel.setText(CURRENCY.format(orderService.totalOf(lines)));
    }

    /** Fills the title, the customer line, the status badge and the status buttons of the cart card. */
    private void showSelectedOrder() {
        OrderSummary o = selectedOrder();
        if (o == null) {
            cartTitle.setText("No order selected");
            cartInfo.setText("Click an order in the list above");
            cartBadge.setStatus("-", Theme.TEXT_MUTED);
            cartHint.setText(" ");
            forwardButton.setText("Send to Kitchen");
            backButton.setText("Back");
            return;
        }
        cartTitle.setText("Order #" + o.getOrderId() + "  -  Table " + o.getTableNumber());
        String phone = o.getCustomerPhone() == null ? "" : "  |  " + o.getCustomerPhone();
        cartInfo.setText(o.getCustomerName() + phone + "  |  " + WHEN.format(o.getCreatedAt()));
        cartBadge.setStatus(o.getStatus().name(), Theme.statusColor(o.getStatus().name()));
        cartHint.setText(lines.isEmpty() ? "No food yet. Click a dish in the menu, then Add to Order." : " ");

        OrderStatus next = o.getStatus().next();
        forwardButton.setText(next == null ? "Order is served" : switch (next) {
            case PREPARING -> "Send to Kitchen";
            case READY -> "Mark Ready";
            default -> "Mark Served";
        });
        OrderStatus previous = o.getStatus().previous();
        backButton.setText(previous == null ? "Back" : "Back to " + previous.getLabel());
        forwardButton.revalidate();
        backButton.revalidate();
    }

    /** Applies the category filter to the menu and redraws the menu table (no database call). */
    private void showMenuFilter() {
        Object chosen = categoryFilter.getSelectedItem();
        menuShown.clear();
        for (MenuItem item : menu) {
            if (chosen == null || ALL_CATEGORIES.equals(chosen) || item.getCategory().equalsIgnoreCase(chosen.toString())) {
                menuShown.add(item);
            }
        }
        filling = true;
        menuModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < menuShown.size(); i++) {
            MenuItem item = menuShown.get(i);
            menuModel.addRow(new Object[]{
                    item.getName(),
                    item.getCategory(),
                    CURRENCY.format(item.getPrice()),
                    item.isAvailable() ? "Available" : "Unavailable"});
            if (item.getId() == selectedDishId) {
                select = i;
            }
        }
        if (select >= 0) {
            menuTable.setRowSelectionInterval(select, select);
        } else {
            menuTable.clearSelection();
            selectedDishId = 0;
        }
        filling = false;
        applyButtonState();
    }

    private OrderSummary selectedOrder() {
        return orders.stream().filter(o -> o.getOrderId() == selectedOrderId).findFirst().orElse(null);
    }

    private OrderLine selectedLine() {
        return lines.stream().filter(l -> l.getItemId() == selectedLineItemId).findFirst().orElse(null);
    }

    private MenuItem selectedDish() {
        return menuShown.stream().filter(m -> m.getId() == selectedDishId).findFirst().orElse(null);
    }

    private void applyButtonState() {
        OrderSummary order = selectedOrder();
        boolean hasOrder = order != null && !busy;
        boolean hasLine = hasOrder && selectedLine() != null;
        MenuItem dish = selectedDish();
        plusButton.setEnabled(hasLine);
        minusButton.setEnabled(hasLine);
        removeButton.setEnabled(hasLine);
        addButton.setEnabled(hasOrder && dish != null && dish.isAvailable());
        forwardButton.setEnabled(hasOrder && order.getStatus().next() != null);
        backButton.setEnabled(hasOrder && order.getStatus().previous() != null);
        seatedButton.setEnabled(!busy);
    }

    // ---------- button actions ----------

    private void onAdd() {
        int orderId = selectedOrderId;
        MenuItem dish = selectedDish();
        int quantity = (Integer) quantitySpinner.getValue();
        runAction(() -> {
            orderService.addItem(orderId, dish, quantity);
            return null;
        }, done -> {
            quantitySpinner.setValue(1);
            refresh();
        });
    }

    /** delta +1 = one more, -1 = one less, 0 = remove the item. */
    private void onLineAction(int delta) {
        int orderId = selectedOrderId;
        int itemId = selectedLineItemId;
        runAction(() -> {
            if (delta > 0) {
                orderService.increaseQuantity(orderId, itemId);
            } else if (delta < 0) {
                orderService.decreaseQuantity(orderId, itemId);
            } else {
                orderService.removeItem(orderId, itemId);
            }
            return null;
        }, done -> refresh());
    }

    private void onMove(boolean forward) {
        OrderSummary order = selectedOrder();
        if (order == null) {
            return;
        }
        OrderStatus target = forward ? order.getStatus().next() : order.getStatus().previous();
        runAction(() -> {
            orderService.changeStatus(order, target);
            return null;
        }, done -> refresh());
    }

    /** Repair path: an occupied table that has no open order. Reuses an order if one exists, else creates ONE. */
    private void onSeatedTable() {
        runAction(orderService::getSeatedTablesWithoutOrder, tables -> {
            if (tables.isEmpty()) {
                Messages.info(this, "Nothing to start", "Every seated table already has an order.\n"
                        + "To seat new customers, use the Tables screen (it creates their order).");
                return;
            }
            String[] labels = tables.stream()
                    .map(t -> "Table " + t.getNumber() + " (" + t.getCapacity() + " seats)")
                    .toArray(String[]::new);
            Object choice = JOptionPane.showInputDialog(this,
                    "These tables have customers but no order. Choose one:", "Order for a seated table",
                    JOptionPane.QUESTION_MESSAGE, null, labels, labels[0]);
            if (choice == null) {
                return;
            }
            TableOverview table = tables.get(java.util.Arrays.asList(labels).indexOf(choice));
            String name = JOptionPane.showInputDialog(this, "Customer name for Table " + table.getNumber() + ":",
                    "Order for a seated table", JOptionPane.QUESTION_MESSAGE);
            if (name == null) {
                return;
            }
            runAction(() -> orderService.openOrderForTable(table.getId(), name), orderId -> {
                selectedOrderId = orderId;
                refresh();
            });
        });
    }

    /** Runs a database action in the background. After a failure the screen is refreshed to show the truth. */
    private <T> void runAction(Callable<T> task, Consumer<T> onSuccess) {
        busy = true;
        applyButtonState();
        Background.run(task, result -> {
            busy = false;
            applyButtonState();
            onSuccess.accept(result);
        }, error -> {
            busy = false;
            applyButtonState();
            Messages.error(this, error);
            if (!(error instanceof InvalidInputException) || selectedOrderId > 0) {
                refresh();
                refreshMenu();
            }
        });
    }
}

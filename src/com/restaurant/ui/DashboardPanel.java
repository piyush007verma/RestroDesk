package com.restaurant.ui;

import com.restaurant.model.DashboardData;
import com.restaurant.model.RecentOrder;
import com.restaurant.model.RestaurantTable;
import com.restaurant.model.TableStatus;
import com.restaurant.service.DashboardService;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.StatCard;
import com.restaurant.ui.components.StatusBadge;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

/**
 * Dashboard screen. OWNER: Member 4.
 * Shows: total orders, revenue, available/occupied tables, recent orders and a table map.
 * Data is loaded in a background thread (SwingWorker) so the window never freezes
 * while the database is being queried.
 */
public class DashboardPanel extends ScreenPanel {

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM, hh:mm a");

    private final DashboardService service;
    private final java.util.function.Consumer<Screen> navigator;
    private final com.restaurant.service.ReportsService reports;
    private final com.restaurant.ui.components.BarChartPanel weekChart =
            new com.restaurant.ui.components.BarChartPanel(Theme.INFO, com.restaurant.service.BillingService::money);

    private final StatCard ordersCard = new StatCard("Total Orders", Theme.INFO);
    private final StatCard revenueCard = new StatCard("Total Revenue", Theme.SUCCESS);
    private final StatCard availableCard = new StatCard("Available Tables", Theme.ACCENT);
    private final StatCard occupiedCard = new StatCard("Occupied Tables", Theme.DANGER);

    private final JLabel noticeLabel = new JLabel();
    private final JLabel resetNote = new JLabel();
    private final DefaultTableModel ordersModel = new DefaultTableModel(
            new String[]{"Order", "Table", "Customer", "Status", "Amount", "Time"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JPanel tableGrid = new JPanel(new GridLayout(0, 3, 10, 10));

    /** Simple form (no clickable shortcuts, no chart). */
    public DashboardPanel(DashboardService service) {
        this(service, screen -> { }, null);
    }

    /**
     * @param navigator opens another screen (cards and quick actions use it)
     * @param reports   source of the "last 7 days" revenue chart (real bills); may be null to hide the data
     */
    public DashboardPanel(DashboardService service, java.util.function.Consumer<Screen> navigator,
                          com.restaurant.service.ReportsService reports) {
        super("Dashboard", "Overview of today's restaurant activity");
        this.service = service;
        this.navigator = navigator;
        this.reports = reports;
        ordersCard.setOnClick(() -> navigator.accept(Screen.HISTORY), "Open Order History");
        revenueCard.setOnClick(() -> navigator.accept(Screen.REPORTS), "Open Reports");
        availableCard.setOnClick(() -> navigator.accept(Screen.TABLES), "Open Tables");
        occupiedCard.setOnClick(() -> navigator.accept(Screen.TABLES), "Open Tables");

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(buildNotice(), BorderLayout.NORTH);
        JPanel statsAndActions = new JPanel(new BorderLayout(0, 14));
        statsAndActions.setOpaque(false);
        statsAndActions.add(buildStatsRow(), BorderLayout.CENTER);
        statsAndActions.add(buildQuickActions(), BorderLayout.SOUTH);
        top.add(statsAndActions, BorderLayout.CENTER);

        JPanel middle = new JPanel(new BorderLayout(18, 0));
        middle.setOpaque(false);
        middle.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
        JPanel leftColumn = new JPanel(new BorderLayout(0, 18));
        leftColumn.setOpaque(false);
        leftColumn.add(buildRecentOrdersCard(), BorderLayout.CENTER);
        leftColumn.add(buildChartCard(), BorderLayout.SOUTH);
        middle.add(leftColumn, BorderLayout.CENTER);
        middle.add(buildTableMapCard(), BorderLayout.EAST);

        body.add(top, BorderLayout.NORTH);
        body.add(middle, BorderLayout.CENTER);
    }

    // ---------- layout helpers ----------

    private JLabel buildNotice() {
        noticeLabel.setFont(Theme.font(java.awt.Font.BOLD, 12));
        noticeLabel.setForeground(Theme.WARNING);
        noticeLabel.setVisible(false);
        return noticeLabel;
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 18, 0));
        row.setOpaque(false);
        row.add(ordersCard);
        row.add(revenueCard);
        row.add(availableCard);
        row.add(occupiedCard);
        return row;
    }

    /** Buttons that jump straight to the most common jobs. Every button opens a real screen. */
    private JPanel buildQuickActions() {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 12, 0));
        row.setOpaque(false);
        row.add(quickAction("New Order", "Seat a customer on a free table (opens Tables)", Theme.ACCENT, Screen.TABLES));
        row.add(quickAction("Add Dish", "Add or edit dishes (opens Menu)", Theme.INFO, Screen.MENU));
        row.add(quickAction("Take Payment", "Bill a served order (opens Billing)", Theme.SUCCESS, Screen.BILLING));
        row.add(quickAction("View Reports", "Sales, GST and best sellers (opens Reports)", new Color(0x7C3AED), Screen.REPORTS));
        if (reports != null) {
            com.restaurant.ui.components.RoundedButton reset = new com.restaurant.ui.components.RoundedButton("Reset Revenue", Theme.DANGER);
            reset.setToolTipText("Start the revenue figures again from zero (no bill is deleted)");
            reset.addActionListener(e -> RevenueResetAction.run(this, reports, this::onShow));
            row.add(reset);
            resetNote.setFont(Theme.font(java.awt.Font.PLAIN, 12));
            resetNote.setForeground(Theme.TEXT_MUTED);
            row.add(resetNote);
        }
        return row;
    }

    private com.restaurant.ui.components.RoundedButton quickAction(String text, String tip, Color color, Screen target) {
        com.restaurant.ui.components.RoundedButton b = new com.restaurant.ui.components.RoundedButton(text, color);
        b.setToolTipText(tip);
        b.addActionListener(e -> navigator.accept(target));
        return b;
    }

    /** Revenue of the last 7 days, drawn from the real paid bills (same numbers as the Reports screen). */
    private RoundedPanel buildChartCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(16, 22, 14, 22));
        card.setPreferredSize(new Dimension(0, 200));
        card.add(cardTitle("Revenue - last 7 days"), BorderLayout.NORTH);
        card.add(weekChart, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel buildRecentOrdersCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        card.add(cardTitle("Recent Orders"), BorderLayout.NORTH);

        JTable table = new JTable(ordersModel);
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Theme.BORDER);
        table.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        table.setForeground(Theme.TEXT_DARK);
        table.setSelectionBackground(new Color(0xFEF3C7));
        table.setSelectionForeground(Theme.TEXT_DARK);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.getColumnModel().getColumn(3).setCellRenderer(new StatusRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(rightAligned());

        int[] widths = {70, 60, 150, 110, 110, 150};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        JTableHeader header = table.getTableHeader();
        header.setFont(Theme.font(java.awt.Font.BOLD, 12));
        header.setPreferredSize(new Dimension(0, 34));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CARD);
        card.add(scroll, BorderLayout.CENTER);
        com.restaurant.ui.components.DataTableStyle.emptyState(table,
                "No orders yet. Seat a customer on a table to create the first one.", "");
        table.setToolTipText("Double-click an order to open Order History");
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    navigator.accept(Screen.HISTORY);
                }
            }
        });
        return card;
    }

    private RoundedPanel buildTableMapCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        card.setPreferredSize(new Dimension(330, 0));
        card.add(cardTitle("Table Status"), BorderLayout.NORTH);

        tableGrid.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(tableGrid, BorderLayout.NORTH);
        card.add(holder, BorderLayout.CENTER);
        return card;
    }

    private static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.BOLD, 16));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static DefaultTableCellRenderer rightAligned() {
        // The default renderer resets the border for every cell, so we set it again after it runs.
        DefaultTableCellRenderer r = new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(javax.swing.JTable t, Object value,
                    boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 24));
                return this;
            }
        };
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }

    // ---------- loading data ----------

    @Override
    public void onShow() {
        loadWeekChart();
        new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() {
                return service.loadDashboard(); // runs off the UI thread
            }

            @Override
            protected void done() {
                try {
                    render(get()); // back on the UI thread
                } catch (InterruptedException | ExecutionException e) {
                    noticeLabel.setText("Could not load dashboard: " + e.getMessage());
                    noticeLabel.setVisible(true);
                }
            }
        }.execute();
    }

    /** Loads the last-7-days revenue for the chart in the background (real bills; the chart stays empty if it fails). */
    private void loadWeekChart() {
        if (reports == null) {
            return;
        }
        Background.run(reports::latestReset, last -> resetNote.setText(RevenueResetAction.sinceShort(last)), error -> resetNote.setText(""));
        Background.run(() -> reports.load(reports.resolve(
                        com.restaurant.service.ReportsService.Period.LAST_7_DAYS, null, null, java.time.LocalDate.now())).daily(),
                rows -> weekChart.setData(
                        rows.stream().map(r -> r.day().format(DateTimeFormatter.ofPattern("dd MMM"))).toList(),
                        rows.stream().map(com.restaurant.model.SalesRow::sales).toList()),
                error -> weekChart.setData(java.util.List.of(), java.util.List.of()));
    }

    private void render(DashboardData data) {
        ordersCard.setValue(String.valueOf(data.getTotalOrders()));
        revenueCard.setValue(CURRENCY.format(data.getTotalRevenue()));
        availableCard.setValue(String.valueOf(data.getAvailableTables()));
        occupiedCard.setValue(String.valueOf(data.getOccupiedTables()));

        noticeLabel.setVisible(data.isDemoMode());
        if (data.isDemoMode()) {
            noticeLabel.setText(Messages.wrap(data.getNotice(), 760));
        }

        ordersModel.setRowCount(0);
        for (RecentOrder o : data.getRecentOrders()) {
            ordersModel.addRow(new Object[]{
                    "#" + o.getOrderId(),
                    "T" + o.getTableNumber(),
                    o.getCustomerName(),
                    o.getStatus(),
                    CURRENCY.format(o.getTotal()),
                    o.getCreatedAt().format(TIME)});
        }

        tableGrid.removeAll();
        for (RestaurantTable t : data.getTables()) {
            tableGrid.add(buildTableTile(t));
        }
        tableGrid.revalidate();
        tableGrid.repaint();
    }

    private JPanel buildTableTile(RestaurantTable t) {
        boolean free = t.getStatus() == TableStatus.AVAILABLE;
        Color accent = free ? Theme.SUCCESS : Theme.DANGER;

        RoundedPanel tile = new RoundedPanel(14, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 22));
        tile.setBorderColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 70));
        tile.setLayout(new GridLayout(3, 1));
        tile.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));
        tile.setPreferredSize(new Dimension(90, 74));

        JLabel number = new JLabel("T" + t.getNumber(), SwingConstants.CENTER);
        number.setFont(Theme.font(java.awt.Font.BOLD, 16));
        number.setForeground(Theme.TEXT_DARK);

        JLabel seats = new JLabel(t.getCapacity() + " seats", SwingConstants.CENTER);
        seats.setFont(Theme.font(java.awt.Font.PLAIN, 11));
        seats.setForeground(Theme.TEXT_MUTED);

        JLabel status = new JLabel(free ? "Free" : "Busy", SwingConstants.CENTER);
        status.setFont(Theme.font(java.awt.Font.BOLD, 11));
        status.setForeground(accent);

        tile.add(number);
        tile.add(seats);
        tile.add(status);
        return tile;
    }

    /** Flat, light table header with only a thin line underneath. */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            setFont(Theme.font(java.awt.Font.BOLD, 12));
            setForeground(Theme.TEXT_MUTED);
            setBackground(Theme.CARD);
            setHorizontalAlignment(column == 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, 2, 0, column == 4 ? 24 : 2)));
            return this;
        }
    }

    /** Draws the Status column as a coloured pill. */
    private static class StatusRenderer extends DefaultTableCellRenderer {
        private final StatusBadge badge = new StatusBadge();

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            String status = String.valueOf(value);
            badge.setStatus(status, Theme.statusColor(status));
            return badge;
        }
    }
}

package com.restaurant.ui;

import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MethodTotal;
import com.restaurant.model.ReportData;
import com.restaurant.model.ReportSummary;
import com.restaurant.model.SalesRow;
import com.restaurant.model.TopItem;
import com.restaurant.service.BillingService;
import com.restaurant.service.ReportsService;
import com.restaurant.service.ReportsService.DateRange;
import com.restaurant.service.ReportsService.Period;
import com.restaurant.ui.components.BarChartPanel;
import com.restaurant.ui.components.DataTableStyle;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.StatCard;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.Scrollable;
import javax.swing.SpinnerDateModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Sales Reports screen. OWNER: Member 4. READ-ONLY. Every number comes from MySQL (SUM / COUNT / AVG / GROUP BY in
 * {@link com.restaurant.dao.ReportsDaoImpl}); money is counted only from bills that are PAID.
 * Choose a period (Today, Last 7 days, This month, All time or a custom range) and all cards, tables and charts update.
 */
public class ReportsPanel extends ScreenPanel {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_SHORT = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final ReportsService service;
    private int loadCounter = 0;

    // period controls
    private final JComboBox<Period> periodBox = new JComboBox<>(Period.values());
    private final JSpinner fromSpinner = dateSpinner();
    private final JSpinner toSpinner = dateSpinner();
    private final RoundedButton applyButton = new RoundedButton("Apply", Theme.ACCENT);
    private final JLabel rangeLabel = new JLabel(" ");
    private final JLabel notice = new JLabel();
    private final JLabel resetNote = new JLabel();
    private final RoundedButton resetButton = new RoundedButton("Reset Revenue", Theme.DANGER);
    private boolean filling = false;

    // cards
    private final StatCard ordersCard = new StatCard("Total Orders", Theme.INFO);
    private final StatCard revenueCard = new StatCard("Total Revenue", Theme.SUCCESS);
    private final StatCard paymentsCard = new StatCard("Payments Done", Theme.SUCCESS);
    private final StatCard pendingCard = new StatCard("Pending Payments", Theme.WARNING);
    private final StatCard averageCard = new StatCard("Avg Order Value", Theme.ACCENT);
    private final JLabel footnote = new JLabel(" ");

    // sections
    private final DefaultTableModel dailyModel = model("Date", "Orders", "Sales", "Discount", "GST");
    private final DefaultTableModel monthlyModel = model("Month", "Orders", "Revenue", "Discount", "GST");
    private final DefaultTableModel topModel = model("Item", "Qty sold", "Sales");
    private final DefaultTableModel methodModel = model("Method", "Payments", "Amount");
    private final BarChartPanel dailyChart = new BarChartPanel(Theme.INFO, BillingService::money);
    private final BarChartPanel monthlyChart = new BarChartPanel(Theme.SUCCESS, BillingService::money);
    private final BarChartPanel topChart = new BarChartPanel(Theme.ACCENT, v -> v.toPlainString());
    private final BarChartPanel methodChart = new BarChartPanel(new java.awt.Color(0x7C3AED), BillingService::money);

    public ReportsPanel(ReportsService service) {
        super("Reports", "Sales and payments, calculated from your saved bills");
        this.service = service;
        fromSpinner.setValue(toDate(LocalDate.now().minusDays(30)));
        toSpinner.setValue(toDate(LocalDate.now()));
        fromSpinner.setEnabled(false);
        toSpinner.setEnabled(false);
        periodBox.setSelectedItem(Period.THIS_MONTH);

        JPanel grid = new JPanel(new GridLayout(2, 2, 18, 18));
        grid.setOpaque(false);
        grid.add(section("Daily sales", dailyChart, new JTable(dailyModel), new int[]{100, 64, 88, 80, 68}, 1, 2, 3, 4));
        grid.add(section("Monthly sales", monthlyChart, new JTable(monthlyModel), new int[]{100, 64, 88, 80, 68}, 1, 2, 3, 4));
        grid.add(section("Best-selling items", topChart, new JTable(topModel), new int[]{200, 70, 100}, 1, 2));
        grid.add(section("Payment methods", methodChart, new JTable(methodModel), new int[]{160, 90, 120}, 1, 2));
        grid.setPreferredSize(new Dimension(900, 780));

        JPanel cards = new JPanel(new GridLayout(1, 5, 14, 0));
        cards.setOpaque(false);
        for (StatCard c : List.of(ordersCard, revenueCard, paymentsCard, pendingCard, averageCard)) {
            c.setPreferredSize(new Dimension(160, 104));
            cards.add(c);
        }
        footnote.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        footnote.setForeground(Theme.TEXT_MUTED);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(buildPeriodCard(), BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);
        top.add(footnote, BorderLayout.SOUTH);

        ScrollableContent content = new ScrollableContent(new BorderLayout(0, 18));
        content.setOpaque(false);
        content.add(top, BorderLayout.NORTH);
        content.add(grid, BorderLayout.CENTER);
        JScrollPane scroll = new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        body.add(scroll, BorderLayout.CENTER);

        periodBox.addActionListener(e -> {
            boolean custom = periodBox.getSelectedItem() == Period.CUSTOM;
            fromSpinner.setEnabled(custom);
            toSpinner.setEnabled(custom);
            if (!filling && !custom) {
                load();
            }
        });
        applyButton.addActionListener(e -> load());
        resetButton.addActionListener(e -> RevenueResetAction.run(this, service, this::load));
    }

    /** Called every time the user opens this screen: recalculate for the chosen period. */
    @Override
    public void onShow() {
        load();
    }

    // ---------- building ----------

    private JPanel buildPeriodCard() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(14, 20, 12, 20));
        card.setLayout(new BorderLayout(0, 4));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        row.setOpaque(false);
        row.add(caption("Period"));
        periodBox.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        periodBox.setBackground(java.awt.Color.WHITE);
        row.add(periodBox);
        row.add(caption("From"));
        row.add(fromSpinner);
        row.add(caption("To"));
        row.add(toSpinner);
        row.add(applyButton);
        rangeLabel.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        rangeLabel.setForeground(Theme.TEXT_MUTED);
        row.add(rangeLabel);
        notice.setFont(Theme.font(java.awt.Font.BOLD, 12));
        notice.setForeground(Theme.WARNING);
        notice.setVisible(false);
        card.add(row, BorderLayout.CENTER);
        resetButton.setToolTipText("Start the revenue and amount figures again from zero (no bill is deleted)");
        row.add(resetButton);
        resetNote.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        resetNote.setForeground(Theme.TEXT_MUTED);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(notice, BorderLayout.NORTH);
        south.add(resetNote, BorderLayout.SOUTH);
        card.add(south, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    private static JPanel section(String title, BarChartPanel chart, JTable table, int[] widths, int... rightColumns) {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        card.setLayout(new BorderLayout(0, 8));
        JLabel t = new JLabel(title);
        t.setFont(Theme.font(java.awt.Font.BOLD, 16));
        t.setForeground(Theme.TEXT_DARK);
        JScrollPane scroll = DataTableStyle.apply(table, rightColumns);
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        scroll.setPreferredSize(new Dimension(10, 150));
        card.add(t, BorderLayout.NORTH);
        card.add(chart, BorderLayout.CENTER);
        card.add(scroll, BorderLayout.SOUTH);
        return card;
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JSpinner dateSpinner() {
        JSpinner s = new JSpinner(new SpinnerDateModel(new Date(), null, null, java.util.Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, "dd MMM yyyy"));
        s.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        s.setPreferredSize(new Dimension(120, 32));
        return s;
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Object value) {
        return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** A panel that is as wide as the scroll area (so only up and down scrolling is needed). */
    private static class ScrollableContent extends JPanel implements Scrollable {
        ScrollableContent(java.awt.LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    // ---------- loading ----------

    private void load() {
        DateRange range;
        try {
            range = service.resolve((Period) periodBox.getSelectedItem(), toLocalDate(fromSpinner.getValue()),
                    toLocalDate(toSpinner.getValue()), LocalDate.now());
        } catch (InvalidInputException e) {
            notice.setText(e.getMessage());
            notice.setToolTipText(null);
            notice.setVisible(true);
            return;
        }
        notice.setVisible(false);
        rangeLabel.setText(range.from().equals(range.to()) ? DAY.format(range.from())
                : DAY.format(range.from()) + "  to  " + DAY.format(range.to()));
        final int ticket = ++loadCounter;
        applyButton.setEnabled(false);
        Background.run(() -> new Loaded(service.load(range), service.latestReset()), loaded -> {
            if (ticket == loadCounter) {
                applyButton.setEnabled(true);
                resetNote.setText(RevenueResetAction.sinceText(loaded.reset()));
                render(loaded.data());
            }
        }, error -> {
            if (ticket == loadCounter) {
                applyButton.setEnabled(true);
                notice.setText(Messages.notice(error));
                notice.setToolTipText(error.getMessage());
                notice.setVisible(true);
            }
        });
    }

    /** Report numbers plus the date of the last revenue reset (loaded together so the screen is always consistent). */
    private record Loaded(ReportData data, com.restaurant.model.RevenueReset reset) {
    }

    private void render(ReportData data) {
        ReportSummary s = data.summary();
        ordersCard.setValue(String.valueOf(s.totalOrders()));
        revenueCard.setValue(BillingService.money(s.revenue()));
        paymentsCard.setValue(String.valueOf(s.completedPayments()));
        pendingCard.setValue(String.valueOf(s.pendingPayments()));
        averageCard.setValue(BillingService.money(s.averageOrderValue()));
        footnote.setText("Revenue counts only PAID bills.  Cancelled orders: " + s.cancelledOrders()
                + "  |  Pending payments = served orders not paid yet  |  Discounts given: "
                + BillingService.money(s.discounts()) + "  |  GST collected: " + BillingService.money(s.gst()));

        fillSales(dailyModel, dailyChart, data.daily(), DAY, DAY_SHORT);
        fillSales(monthlyModel, monthlyChart, data.monthly(), MONTH, MONTH);

        topModel.setRowCount(0);
        for (TopItem t : data.topItems()) {
            topModel.addRow(new Object[]{t.name(), t.quantity(), BillingService.money(t.sales())});
        }
        topChart.setData(data.topItems().stream().map(TopItem::name).toList(),
                data.topItems().stream().map(t -> BigDecimal.valueOf(t.quantity())).toList());

        methodModel.setRowCount(0);
        for (MethodTotal m : data.methods()) {
            methodModel.addRow(new Object[]{m.method().getLabel(), m.payments(), BillingService.money(m.amount())});
        }
        boolean anyPayment = data.methods().stream().anyMatch(m -> m.payments() > 0);
        methodChart.setData(anyPayment ? data.methods().stream().map(m -> m.method().getLabel()).toList() : List.of(),
                anyPayment ? data.methods().stream().map(MethodTotal::amount).toList() : List.of());
    }

    private static void fillSales(DefaultTableModel model, BarChartPanel chart, List<SalesRow> rows,
                                  DateTimeFormatter tableFormat, DateTimeFormatter chartFormat) {
        model.setRowCount(0);
        for (SalesRow r : rows) {
            model.addRow(new Object[]{tableFormat.format(r.day()), r.orders(), BillingService.money(r.sales()),
                    BillingService.money(r.discounts()), BillingService.money(r.gst())});
        }
        Function<SalesRow, String> label = r -> chartFormat.format(r.day());
        chart.setData(rows.stream().map(label).toList(), rows.stream().map(SalesRow::sales).toList());
    }
}

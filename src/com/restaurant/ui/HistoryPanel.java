package com.restaurant.ui;

import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.HistoryDetails;
import com.restaurant.model.HistoryFilter;
import com.restaurant.model.HistoryRow;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.PaymentMethod;
import com.restaurant.service.BillingService;
import com.restaurant.service.HistoryService;
import com.restaurant.ui.components.DataTableStyle;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.StatusBadge;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerDateModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Order History screen. OWNER: Member 4. READ-ONLY: it can search, filter and show old orders and bills,
 * but it has no button that changes or deletes anything.
 * <pre>
 *  top     filters (order id, table, status, payment status, payment method, date range)
 *  left    matching orders, newest first
 *  right   details of the selected order: items, bill breakdown, payment, View Receipt
 * </pre>
 */
public class HistoryPanel extends ScreenPanel {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm", Locale.ENGLISH);
    private static final String ALL_STATUSES = "All statuses";
    private static final String ALL_PAYMENTS = "All payments";
    private static final String ALL_METHODS = "All methods";
    private static final String PAID_LABEL = "Paid";
    private static final String UNPAID_LABEL = "Unpaid";

    private final HistoryService service;

    private final List<HistoryRow> rows = new ArrayList<>();
    private HistoryDetails shownDetails;
    private int selectedOrderId = 0;
    private boolean filling = false;
    private int loadCounter = 0; // lets a slow, old answer be ignored when the user already asked again

    // filters
    private final JTextField orderIdField = new JTextField(6);
    private final JTextField tableField = new JTextField(5);
    private final JComboBox<Object> statusBox = new JComboBox<>();
    private final JComboBox<String> paymentBox = new JComboBox<>(new String[]{ALL_PAYMENTS, PAID_LABEL, UNPAID_LABEL});
    private final JComboBox<Object> methodBox = new JComboBox<>();
    private final JCheckBox dateCheck = new JCheckBox("Filter by date");
    private final JSpinner fromSpinner = dateSpinner();
    private final JSpinner toSpinner = dateSpinner();
    private final RoundedButton searchButton = new RoundedButton("Search", Theme.ACCENT);
    private final RoundedButton resetButton = new RoundedButton("Reset", Theme.TEXT_MUTED);
    private final JCheckBox clearedCheck = new JCheckBox("Show cleared orders");
    private final JLabel filterNotice = new JLabel();
    private final RoundedButton clearButton = new RoundedButton("Clear Order History", Theme.DANGER);
    private final JLabel clearedLegend = new JLabel(" ");

    // list
    private final DefaultTableModel listModel = readOnlyModel("ID", "Table", "Customer", "Date", "Status", "Total", "Payment", "Method");
    private final JTable listTable = new JTable(listModel);
    private final JLabel countLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel();

    // details
    private final JLabel detailTitle = new JLabel("No order selected");
    private final JLabel detailInfo = new JLabel("Click an order in the list");
    private final StatusBadge orderBadge = new StatusBadge();
    private final StatusBadge payBadge = new StatusBadge();
    private final DefaultTableModel itemModel = readOnlyModel("Item", "Qty", "Price", "Subtotal");
    private final JTable itemTable = new JTable(itemModel);
    private final JLabel subtotalValue = valueLabel();
    private final JLabel discountValue = valueLabel();
    private final JLabel taxableValue = valueLabel();
    private final JLabel gstValue = valueLabel();
    private final JLabel totalValue = valueLabel();
    private final JLabel methodValue = valueLabel();
    private final JLabel paidAtValue = valueLabel();
    private final JLabel billNote = new JLabel(" ");
    private final RoundedButton receiptButton = new RoundedButton("View Receipt", Theme.INFO);

    public HistoryPanel(HistoryService service) {
        super("Order History", "Search and review every order, its bill and its payment");
        this.service = service;

        statusBox.addItem(ALL_STATUSES);
        for (OrderStatus s : OrderStatus.values()) {
            statusBox.addItem(s);
        }
        methodBox.addItem(ALL_METHODS);
        for (PaymentMethod m : PaymentMethod.values()) {
            methodBox.addItem(m);
        }
        fromSpinner.setValue(toDate(LocalDate.now().minusDays(30)));
        toSpinner.setValue(toDate(LocalDate.now()));

        JPanel centre = new JPanel(new BorderLayout(18, 0));
        centre.setOpaque(false);
        centre.add(buildListCard(), BorderLayout.CENTER);
        centre.add(buildDetailsCard(), BorderLayout.EAST);

        JPanel wrapper = new JPanel(new BorderLayout(0, 14));
        wrapper.setOpaque(false);
        wrapper.add(buildFilterCard(), BorderLayout.NORTH);
        wrapper.add(centre, BorderLayout.CENTER);
        body.add(wrapper, BorderLayout.CENTER);

        wireActions();
        showDetails(null);
    }

    /** Called every time the user opens this screen: reload with the filters as they are. */
    @Override
    public void onShow() {
        search();
    }

    // ---------- building the screen ----------

    private JPanel buildFilterCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 6));

        JPanel row1 = flow();
        row1.add(captioned("Order ID", orderIdField));
        row1.add(captioned("Table number", tableField));
        row1.add(captioned("Order status", statusBox));
        row1.add(captioned("Payment status", paymentBox));
        row1.add(captioned("Payment method", methodBox));

        JPanel row2 = flow();
        dateCheck.setOpaque(false);
        dateCheck.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        row2.add(captioned(" ", dateCheck));
        row2.add(captioned("From", fromSpinner));
        row2.add(captioned("To", toSpinner));
        row2.add(captioned(" ", searchButton));
        row2.add(captioned(" ", resetButton));
        clearedCheck.setOpaque(false);
        clearedCheck.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        clearedCheck.setToolTipText("Cleared orders are only hidden, never deleted. Tick this to see them again.");
        row2.add(captioned(" ", clearedCheck));
        fromSpinner.setEnabled(false);
        toSpinner.setEnabled(false);

        filterNotice.setFont(Theme.font(java.awt.Font.BOLD, 12));
        filterNotice.setForeground(Theme.WARNING);
        filterNotice.setVisible(false);

        JPanel rows = new JPanel();
        rows.setOpaque(false);
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        rows.add(row1);
        rows.add(row2);
        card.add(rows, BorderLayout.CENTER);
        card.add(filterNotice, BorderLayout.SOUTH);
        for (JComboBox<?> box : List.of(statusBox, paymentBox, methodBox)) {
            box.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            box.setBackground(java.awt.Color.WHITE);
        }
        for (JTextField f : List.of(orderIdField, tableField)) {
            f.setFont(Theme.font(java.awt.Font.PLAIN, 13));
            f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        }
        return card;
    }

    private JPanel buildListCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        JLabel title = cardTitle("Orders");
        countLabel.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        countLabel.setForeground(Theme.TEXT_MUTED);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        clearedLegend.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        clearedLegend.setForeground(Theme.TEXT_MUTED);
        JPanel titleRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        titleRight.setOpaque(false);
        titleRight.add(clearedLegend);
        titleRight.add(countLabel);
        titleRight.add(clearButton);
        clearButton.setToolTipText("Hides finished orders from this list. Bills, payments and reports are NOT affected.");
        titleRow.add(titleRight, BorderLayout.EAST);
        emptyLabel.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        emptyLabel.setForeground(Theme.TEXT_MUTED);
        card.add(titleRow, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(listTable, 5), BorderLayout.CENTER);
        DataTableStyle.enableSorting(listTable);
        DataTableStyle.emptyState(listTable, "No orders to show. New orders appear here as soon as they are created.", "No order matches.");
        card.add(emptyLabel, BorderLayout.SOUTH);
        listTable.getColumnModel().getColumn(4).setCellRenderer(new DataTableStyle.BadgeRenderer(Theme::statusColor));
        listTable.getColumnModel().getColumn(6).setCellRenderer(new DataTableStyle.BadgeRenderer(HistoryPanel::paymentColor));
        setWidths(listTable, 44, 56, 86, 114, 88, 80, 78, 66);
        return card;
    }

    private JPanel buildDetailsCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        card.setPreferredSize(new Dimension(340, 0));

        detailTitle.setFont(Theme.font(java.awt.Font.BOLD, 16));
        detailTitle.setForeground(Theme.TEXT_DARK);
        detailInfo.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        detailInfo.setForeground(Theme.TEXT_MUTED);
        JPanel texts = new JPanel(new BorderLayout(0, 2));
        texts.setOpaque(false);
        texts.add(detailTitle, BorderLayout.NORTH);
        texts.add(detailInfo, BorderLayout.CENTER);
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        badges.setOpaque(false);
        badges.add(orderBadge);
        badges.add(payBadge);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(texts, BorderLayout.CENTER);
        header.add(badges, BorderLayout.EAST);

        JPanel bill = new JPanel(new GridLayout(0, 2, 8, 4));
        bill.setOpaque(false);
        addPair(bill, "Subtotal", subtotalValue);
        addPair(bill, "Discount", discountValue);
        addPair(bill, "Taxable amount", taxableValue);
        addPair(bill, "GST", gstValue);
        addPair(bill, "Grand total", totalValue);
        addPair(bill, "Payment method", methodValue);
        addPair(bill, "Paid at", paidAtValue);
        totalValue.setFont(Theme.font(java.awt.Font.BOLD, 15));
        billNote.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        billNote.setForeground(Theme.TEXT_MUTED);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.Y_AXIS));
        footer.add(left(bill));
        footer.add(Box.createVerticalStrut(4));
        footer.add(left(billNote));
        footer.add(Box.createVerticalStrut(8));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.add(receiptButton);
        footer.add(left(buttons));

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(itemTable, 1, 2, 3), BorderLayout.CENTER);
        card.add(footer, BorderLayout.SOUTH);
        setWidths(itemTable, 96, 64, 76, 84);
        return card;
    }

    private void addPair(JPanel panel, String caption, JLabel value) {
        JLabel c = new JLabel(caption);
        c.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        c.setForeground(Theme.TEXT_MUTED);
        panel.add(c);
        panel.add(value);
    }

    private static JLabel valueLabel() {
        JLabel l = new JLabel("-", JLabel.RIGHT);
        l.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static JPanel flow() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel captioned(String caption, Component control) {
        JPanel p = new JPanel(new BorderLayout(0, 3));
        p.setOpaque(false);
        JLabel l = new JLabel(caption);
        l.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        l.setForeground(Theme.TEXT_MUTED);
        p.add(l, BorderLayout.NORTH);
        p.add(control, BorderLayout.CENTER);
        return p;
    }

    private static JSpinner dateSpinner() {
        JSpinner s = new JSpinner(new SpinnerDateModel(new Date(), null, null, java.util.Calendar.DAY_OF_MONTH));
        s.setEditor(new JSpinner.DateEditor(s, "dd MMM yyyy"));
        s.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        s.setPreferredSize(new Dimension(120, 32));
        return s;
    }

    private static RoundedPanel card() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));
        return card;
    }

    private static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.BOLD, 16));
        l.setForeground(Theme.TEXT_DARK);
        return l;
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

    private static java.awt.Color paymentColor(String text) {
        return switch (text) {
            case "PAID" -> Theme.SUCCESS;
            case "UNPAID" -> Theme.WARNING;
            default -> Theme.TEXT_MUTED;
        };
    }

    private static Date toDate(LocalDate d) {
        return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static LocalDate toLocalDate(Object value) {
        return ((Date) value).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // ---------- actions ----------

    private void wireActions() {
        searchButton.addActionListener(e -> search());
        orderIdField.addActionListener(e -> search());
        tableField.addActionListener(e -> search());
        clearedCheck.addActionListener(e -> search());
        clearButton.addActionListener(e -> clearHistory());
        statusBox.addActionListener(e -> search());
        paymentBox.addActionListener(e -> search());
        methodBox.addActionListener(e -> search());
        dateCheck.addActionListener(e -> {
            fromSpinner.setEnabled(dateCheck.isSelected());
            toSpinner.setEnabled(dateCheck.isSelected());
            search();
        });
        javax.swing.event.ChangeListener dateChanged = e -> {
            if (dateCheck.isSelected()) {
                search();
            }
        };
        fromSpinner.addChangeListener(dateChanged);
        toSpinner.addChangeListener(dateChanged);
        resetButton.addActionListener(e -> {
            filling = true;
            orderIdField.setText("");
            tableField.setText("");
            statusBox.setSelectedIndex(0);
            paymentBox.setSelectedIndex(0);
            methodBox.setSelectedIndex(0);
            dateCheck.setSelected(false);
            clearedCheck.setSelected(false);
            fromSpinner.setEnabled(false);
            toSpinner.setEnabled(false);
            fromSpinner.setValue(toDate(LocalDate.now().minusDays(30)));
            toSpinner.setValue(toDate(LocalDate.now()));
            filling = false;
            search();
        });
        listTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = DataTableStyle.selectedModelRow(listTable);
            if (row >= 0 && row < rows.size()) {
                selectedOrderId = rows.get(row).orderId();
                loadDetails(rows.get(row));
            }
        });
        receiptButton.addActionListener(e -> {
            if (shownDetails != null && shownDetails.bill() != null) {
                ReceiptDialog.show(this, "Receipt - Bill #" + shownDetails.bill().getBillId(), service.receiptOf(shownDetails));
            }
        });
    }

    /**
     * Clear Order History: check that migration 003 was run, count what would be hidden, ask for confirmation (Cancel changes
     * nothing), then hide the finished orders. Nothing is deleted: bills, payments, GST, discounts and reports stay as they are.
     */
    private void clearHistory() {
        clearButton.setEnabled(false);
        Background.run(() -> {
            if (!service.isClearingAvailable()) {
                return -1;
            }
            return service.countClearableOrders();
        }, count -> {
            if (count < 0) {
                clearButton.setEnabled(true);
                Messages.info(this, "One-time database update needed",
                        "To use Clear Order History, run this file once in MySQL Workbench:\n"
                                + "database/migrations/003_add_history_cleared_flag.sql\n\n"
                                + "It only adds one column and keeps all your data. Then restart RestoDesk.");
                return;
            }
            if (count == 0) {
                clearButton.setEnabled(true);
                Messages.info(this, "Nothing to clear", "There are no finished (paid or cancelled) orders in the list.\n"
                        + "Open orders are never cleared.");
                return;
            }
            Object[] choices = {"Clear History", "Cancel"};
            int answer = JOptionPane.showOptionDialog(this,
                    "This will remove " + count + " finished (paid or cancelled) order" + (count == 1 ? "" : "s")
                            + " from the Order History list.\n\n"
                            + "Nothing is deleted. Bills, payments, GST, discounts, revenue and reports stay exactly as they are.\n"
                            + "Open orders (pending, preparing, ready, served) are not affected.\n"
                            + "You can still see cleared orders by ticking \"Show cleared orders\".",
                    "Clear Order History?", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[1]);
            if (answer != 0) {
                clearButton.setEnabled(true);
                return;
            }
            Background.run(service::clearHistory, hidden -> {
                clearButton.setEnabled(true);
                selectedOrderId = 0;
                search();
                Messages.info(this, "Order History cleared", hidden + " order" + (hidden == 1 ? " was" : "s were")
                        + " removed from the list.\nBills, payments and reports were not changed.");
            }, error -> {
                clearButton.setEnabled(true);
                Messages.error(this, error);
                search();
            });
        }, error -> {
            clearButton.setEnabled(true);
            Messages.error(this, error);
        });
    }

    /** Reads the filter boxes, validates them and loads the matching orders in the background. */
    private void search() {
        if (filling) {
            return;
        }
        HistoryFilter filter;
        try {
            Object st = statusBox.getSelectedItem();
            Object me = methodBox.getSelectedItem();
            String pay = PAID_LABEL.equals(paymentBox.getSelectedItem())
                    ? HistoryFilter.PAID
                    : UNPAID_LABEL.equals(paymentBox.getSelectedItem()) ? HistoryFilter.UNPAID : null;
            filter = service.buildFilter(orderIdField.getText(), tableField.getText(),
                    st instanceof OrderStatus s ? s : null, pay, me instanceof PaymentMethod m ? m : null,
                    dateCheck.isSelected() ? toLocalDate(fromSpinner.getValue()) : null,
                    dateCheck.isSelected() ? toLocalDate(toSpinner.getValue()) : null);
        } catch (InvalidInputException e) {
            filterNotice.setText(e.getMessage());
            filterNotice.setVisible(true);
            return;
        }
        filterNotice.setVisible(false);
        final HistoryFilter filterWithCleared = filter.withIncludeCleared(clearedCheck.isSelected());
        final int ticket = ++loadCounter;
        Background.run(() -> service.search(filterWithCleared), result -> {
            if (ticket == loadCounter) {
                render(result);
            }
        }, error -> {
            if (ticket != loadCounter) {
                return;
            }
            rows.clear();
            listModel.setRowCount(0);
            countLabel.setText("");
            emptyLabel.setText(" ");
            filterNotice.setText(Messages.notice(error));
            filterNotice.setToolTipText(error.getMessage());
            filterNotice.setVisible(true);
            showDetails(null);
        });
    }

    private void render(List<HistoryRow> result) {
        rows.clear();
        rows.addAll(result);
        filling = true;
        listModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < rows.size(); i++) {
            HistoryRow r = rows.get(i);
            listModel.addRow(new Object[]{
                    "#" + r.orderId() + (r.cleared() ? "*" : ""),
                    "T" + r.tableNumber(),
                    r.customerName(),
                    WHEN.format(r.createdAt()),
                    r.status().name(),
                    r.total() == null ? "-" : BillingService.money(r.total()),
                    r.paymentStatus(),
                    r.paymentMethod() == null ? "-" : r.paymentMethod().getLabel()});
            if (r.orderId() == selectedOrderId) {
                select = i;
            }
        }
        if (select >= 0) {
            DataTableStyle.selectModelRow(listTable, select);
        } else {
            listTable.clearSelection();
        }
        filling = false;
        boolean cut = rows.size() >= HistoryService.MAX_ROWS;
        countLabel.setText(rows.size() == 1 ? "1 order" : rows.size() + " orders" + (cut ? " (newest " + HistoryService.MAX_ROWS + " shown)" : ""));
        clearedLegend.setText(rows.stream().anyMatch(HistoryRow::cleared) ? "* = cleared from history" : " ");
        emptyLabel.setText(rows.isEmpty() ? "No orders match these filters. Change them or click Reset." : " ");
        if (select >= 0) {
            loadDetails(rows.get(select));
        } else {
            selectedOrderId = 0;
            showDetails(null);
        }
    }

    private void loadDetails(HistoryRow row) {
        Background.run(() -> service.getDetails(row), details -> {
            if (row.orderId() == selectedOrderId) {
                showDetails(details);
            }
        }, error -> {
            showDetails(null);
            Messages.error(this, error);
        });
    }

    private void showDetails(HistoryDetails d) {
        shownDetails = d;
        itemModel.setRowCount(0);
        if (d == null) {
            detailTitle.setText("No order selected");
            detailInfo.setText("Click an order in the list");
            orderBadge.setStatus("-", Theme.TEXT_MUTED);
            payBadge.setStatus("-", Theme.TEXT_MUTED);
            for (JLabel l : List.of(subtotalValue, discountValue, taxableValue, gstValue, totalValue, methodValue, paidAtValue)) {
                l.setText("-");
            }
            billNote.setText(" ");
            receiptButton.setEnabled(false);
            return;
        }
        HistoryRow r = d.row();
        detailTitle.setText("Order #" + r.orderId() + "  -  Table " + r.tableNumber());
        detailInfo.setText(r.customerName() + "  |  " + WHEN.format(r.createdAt()));
        orderBadge.setStatus(r.status().name(), Theme.statusColor(r.status().name()));
        payBadge.setStatus(r.paymentStatus(), paymentColor(r.paymentStatus()));
        for (OrderLine l : d.lines()) {
            itemModel.addRow(new Object[]{l.getName(), l.getQuantity(), BillingService.money(l.getUnitPrice()),
                    BillingService.money(l.getSubtotal())});
        }
        if (d.bill() != null) {
            var c = d.bill().getCalculation();
            subtotalValue.setText(BillingService.money(c.getSubtotal()));
            String discountText = switch (c.getDiscountType()) {
                case PERCENT -> " (" + c.getDiscountValue().stripTrailingZeros().toPlainString() + "%)";
                case FIXED -> " (fixed)";
                default -> "";
            };
            discountValue.setText("-" + BillingService.money(c.getDiscountAmount()) + discountText);
            taxableValue.setText(BillingService.money(c.getTaxableAmount()));
            gstValue.setText(BillingService.money(c.getGstAmount()) + " (" + c.getGstPercent().stripTrailingZeros().toPlainString() + "%)");
            totalValue.setText(BillingService.money(c.getGrandTotal()));
            methodValue.setText(d.bill().getPaymentMethod().getLabel());
            paidAtValue.setText(WHEN.format(d.bill().getPaidAt()));
            billNote.setText(r.cleared() ? "Cleared from the history list. The bill and payment are still saved and counted in Reports." : " ");
            receiptButton.setEnabled(true);
        } else {
            BigDecimal items = d.lines().stream().map(OrderLine::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            subtotalValue.setText(BillingService.money(items));
            for (JLabel l : List.of(discountValue, taxableValue, gstValue, totalValue, methodValue, paidAtValue)) {
                l.setText("-");
            }
            billNote.setText(r.status() == OrderStatus.CANCELLED ? "This order was cancelled. No bill was made."
                    : "No bill yet. The amount above is the sum of the items.");
            receiptButton.setEnabled(false);
        }
    }
}

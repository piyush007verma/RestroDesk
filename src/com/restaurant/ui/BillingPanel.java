package com.restaurant.ui;

import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.Bill;
import com.restaurant.model.BillCalculation;
import com.restaurant.model.DiscountType;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.PaymentMethod;
import com.restaurant.service.BillingService;
import com.restaurant.ui.components.DataTableStyle;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.StatusBadge;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Billing screen. OWNER: Member 3.
 * <pre>
 *  left, top    "Ready to bill": SERVED orders (or "Paid bills": the saved bills, newest first)
 *  left, bottom the food items of the selected order
 *  right        the bill: discount, GST, breakdown, payment method, Confirm Payment, Receipt
 * </pre>
 * Choosing an order, typing a discount or choosing a payment method only CALCULATES on screen; nothing is saved.
 * The database changes only when the user presses Confirm Payment and says Yes: then the payment and the table
 * release happen together in one transaction ({@link BillingService#pay}). No SQL and no rules in this class.
 */
public class BillingPanel extends ScreenPanel {

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.ENGLISH);
    private static final String MODE_READY = "Ready to bill";
    private static final String MODE_PAID = "Paid bills";

    private final BillingService service;

    // what is on screen
    private final List<OrderSummary> orders = new ArrayList<>();
    private final List<Bill> bills = new ArrayList<>();
    private final List<OrderLine> lines = new ArrayList<>();
    private int selectedOrderId = 0;
    private int selectedBillId = 0;
    private Bill shownBill = null;           // the saved bill whose details are on the right (paid mode)
    private BillCalculation current = null;   // the calculation of the typed values (ready mode); null when invalid
    private boolean busy = false;
    private boolean filling = false;

    // list card
    private final JComboBox<String> modeBox = new JComboBox<>(new String[]{MODE_READY, MODE_PAID});
    private final DefaultTableModel listModel = readOnly();
    private final JTable listTable = new JTable(listModel);
    private final JLabel listCount = new JLabel();
    private final JLabel listNotice = new JLabel();

    // items card
    private final DefaultTableModel itemsModel = readOnly("Item", "Qty", "Price", "Subtotal");
    private final JTable itemsTable = new JTable(itemsModel);

    // bill card
    private final JLabel billTitle = new JLabel("No order selected");
    private final JLabel billInfo = new JLabel("Click an order in the list");
    private final StatusBadge billBadge = new StatusBadge();
    private final JComboBox<DiscountType> discountType = new JComboBox<>(DiscountType.values());
    private final JTextField discountField = new JTextField("0", 6);
    private final JTextField gstField = new JTextField(BillingService.DEFAULT_GST_PERCENT.toPlainString(), 6);
    private final JLabel subtotalValue = value();
    private final JLabel discountValue = value();
    private final JLabel taxableValue = value();
    private final JLabel gstValue = value();
    private final JLabel gstLabel = label("GST");
    private final JLabel totalValue = new JLabel("–");
    private final JComboBox<PaymentMethod> methodBox = new JComboBox<>(PaymentMethod.values());
    private final JLabel messageLabel = new JLabel(" ");
    private final RoundedButton payButton = new RoundedButton("Confirm Payment", Theme.ACCENT);
    private final RoundedButton receiptButton = new RoundedButton("View Receipt", Theme.TEXT_MUTED);

    public BillingPanel(BillingService service) {
        super("Billing", "Check the bill, choose how the customer pays and confirm - the table is freed after payment");
        this.service = service;

        JPanel listCard = buildListCard();
        JPanel itemsCard = buildItemsCard();
        JPanel left = new JPanel(new BorderLayout(0, 18));
        left.setOpaque(false);
        left.add(listCard, BorderLayout.NORTH);
        left.add(itemsCard, BorderLayout.CENTER);
        listCard.setPreferredSize(new Dimension(10, 270));

        body.setLayout(new BorderLayout(18, 0));
        body.add(left, BorderLayout.CENTER);
        body.add(buildBillCard(), BorderLayout.EAST);

        wireActions();
        showNothing();
    }

    @Override
    public void onShow() {
        refreshList();
    }

    // ---------- building the screen ----------

    private JPanel buildListCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        JLabel title = cardTitle("Orders");
        modeBox.setFont(Theme.font(Font.PLAIN, 13));
        modeBox.setBackground(Color.WHITE);
        listCount.setFont(Theme.font(Font.PLAIN, 13));
        listCount.setForeground(Theme.TEXT_MUTED);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(listCount);
        right.add(modeBox);
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(right, BorderLayout.EAST);

        listNotice.setFont(Theme.font(Font.BOLD, 12));
        listNotice.setForeground(Theme.WARNING);
        listNotice.setVisible(false);
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(listNotice, BorderLayout.SOUTH);

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(listTable, 3, 4), BorderLayout.CENTER);
        DataTableStyle.enableSorting(listTable);
        DataTableStyle.emptyState(listTable, "Nothing here yet. Orders can be billed once they are marked Served.", "No match.");
        setReadyColumns();
        return card;
    }

    private JPanel buildItemsCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 8));
        card.add(cardTitle("Food items of the selected order"), BorderLayout.NORTH);
        card.add(DataTableStyle.apply(itemsTable, 1, 2, 3), BorderLayout.CENTER);
        itemsTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        itemsTable.getColumnModel().getColumn(1).setPreferredWidth(50);
        itemsTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        itemsTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        return card;
    }

    private JPanel buildBillCard() {
        RoundedPanel card = card();
        card.setLayout(new BorderLayout(0, 10));
        card.setPreferredSize(new Dimension(390, 0));

        billTitle.setFont(Theme.font(Font.BOLD, 16));
        billTitle.setForeground(Theme.TEXT_DARK);
        billInfo.setFont(Theme.font(Font.PLAIN, 12));
        billInfo.setForeground(Theme.TEXT_MUTED);
        billBadge.setStatus("-", Theme.TEXT_MUTED);
        JPanel texts = new JPanel(new BorderLayout(0, 2));
        texts.setOpaque(false);
        texts.add(billTitle, BorderLayout.NORTH);
        texts.add(billInfo, BorderLayout.CENTER);
        JPanel badgeHolder = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        badgeHolder.setOpaque(false);
        badgeHolder.add(billBadge);
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(texts, BorderLayout.CENTER);
        header.add(badgeHolder, BorderLayout.EAST);

        // form: discount + GST
        discountType.setFont(Theme.font(Font.PLAIN, 13));
        discountType.setBackground(Color.WHITE);
        styleField(discountField);
        styleField(gstField);
        discountField.setColumns(7);
        gstField.setColumns(7);
        JPanel discountRow = new JPanel(new BorderLayout(8, 0));
        discountRow.setOpaque(false);
        discountRow.add(discountType, BorderLayout.CENTER);
        discountRow.add(discountField, BorderLayout.EAST);
        JPanel gstRow = new JPanel(new BorderLayout(8, 0));
        gstRow.setOpaque(false);
        gstRow.add(label("GST percentage (%)"), BorderLayout.CENTER);
        gstRow.add(gstField, BorderLayout.EAST);

        // breakdown
        JPanel breakdown = new JPanel(new GridLayout(0, 2, 0, 6));
        breakdown.setOpaque(false);
        breakdown.add(label("Subtotal"));
        breakdown.add(subtotalValue);
        breakdown.add(label("Discount"));
        breakdown.add(discountValue);
        breakdown.add(label("Taxable amount"));
        breakdown.add(taxableValue);
        breakdown.add(gstLabel);
        breakdown.add(gstValue);

        JLabel totalText = new JLabel("Grand Total");
        totalText.setFont(Theme.font(Font.BOLD, 15));
        totalText.setForeground(Theme.TEXT_DARK);
        totalValue.setFont(Theme.font(Font.BOLD, 24));
        totalValue.setForeground(Theme.TEXT_DARK);
        totalValue.setHorizontalAlignment(JLabel.RIGHT);
        totalValue.setName("grandTotal");
        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        totalRow.add(totalText, BorderLayout.WEST);
        totalRow.add(totalValue, BorderLayout.EAST);

        methodBox.setFont(Theme.font(Font.PLAIN, 13));
        methodBox.setBackground(Color.WHITE);
        JPanel methodRow = new JPanel(new BorderLayout(8, 0));
        methodRow.setOpaque(false);
        methodRow.add(label("Payment method"), BorderLayout.CENTER);
        methodRow.add(methodBox, BorderLayout.EAST);

        messageLabel.setFont(Theme.font(Font.BOLD, 12));
        messageLabel.setForeground(Theme.WARNING);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(payButton);
        buttons.add(receiptButton);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.add(left(label("Discount")));
        form.add(Box.createVerticalStrut(4));
        form.add(left(discountRow));
        form.add(Box.createVerticalStrut(10));
        form.add(left(gstRow));
        form.add(Box.createVerticalStrut(16));
        form.add(left(breakdown));
        form.add(Box.createVerticalStrut(12));
        form.add(left(totalRow));
        form.add(Box.createVerticalStrut(16));
        form.add(left(methodRow));
        form.add(Box.createVerticalStrut(8));
        form.add(left(messageLabel));
        form.add(Box.createVerticalStrut(8));
        form.add(left(buttons));

        card.add(header, BorderLayout.NORTH);
        card.add(form, BorderLayout.CENTER);
        return card;
    }

    private static RoundedPanel card() {
        RoundedPanel card = new RoundedPanel();
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 16, 20));
        return card;
    }

    private static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.BOLD, 16));
        l.setForeground(Theme.TEXT_DARK);
        return l;
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(Font.PLAIN, 13));
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    private static JLabel value() {
        JLabel l = new JLabel("–");
        l.setFont(Theme.font(Font.PLAIN, 14));
        l.setForeground(Theme.TEXT_DARK);
        l.setHorizontalAlignment(JLabel.RIGHT);
        return l;
    }

    private static void styleField(JTextField f) {
        f.setFont(Theme.font(Font.PLAIN, 14));
        f.setHorizontalAlignment(JTextField.RIGHT);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
    }

    /** Left-aligns a row and stops BoxLayout from stretching it taller than it needs to be. */
    private static JPanel left(JPanel p) {
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    private static JLabel left(JLabel l) {
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static DefaultTableModel readOnly(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private boolean paidMode() {
        return MODE_PAID.equals(modeBox.getSelectedItem());
    }

    /** Columns of the "Ready to bill" list. */
    private void setReadyColumns() {
        listModel.setColumnIdentifiers(new String[]{"Order", "Table", "Customer", "Qty", "Total", "Status"});
        int[] w = {50, 50, 130, 40, 90, 90};
        for (int i = 0; i < w.length; i++) {
            listTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
        listTable.getColumnModel().getColumn(5).setCellRenderer(new DataTableStyle.BadgeRenderer(Theme::statusColor));
    }

    /** Columns of the "Paid bills" list. */
    private void setPaidColumns() {
        listModel.setColumnIdentifiers(new String[]{"Bill", "Table", "Customer", "Paid", "Total", "Method"});
        int[] w = {45, 50, 130, 85, 90, 70};
        for (int i = 0; i < w.length; i++) {
            listTable.getColumnModel().getColumn(i).setPreferredWidth(w[i]);
        }
    }

    // ---------- connecting clicks to actions ----------

    private void wireActions() {
        modeBox.addActionListener(e -> {
            if (filling) {
                return;
            }
            selectedOrderId = 0;
            selectedBillId = 0;
            shownBill = null;
            lines.clear();
            fillItems();
            if (paidMode()) {
                setPaidColumns();
            } else {
                setReadyColumns();
            }
            showNothing();
            refreshList();
        });
        listTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = DataTableStyle.selectedModelRow(listTable);
            if (paidMode()) {
                if (row >= 0 && row < bills.size() && bills.get(row).getBillId() != selectedBillId) {
                    selectedBillId = bills.get(row).getBillId();
                    loadPaidBill(selectedBillId);
                }
            } else if (row >= 0 && row < orders.size() && orders.get(row).getOrderId() != selectedOrderId) {
                selectedOrderId = orders.get(row).getOrderId();
                loadOrderLines();
            }
        });
        DocumentListener recalc = new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { recalculate(); }
            public void removeUpdate(DocumentEvent e) { recalculate(); }
            public void changedUpdate(DocumentEvent e) { recalculate(); }
        };
        discountField.getDocument().addDocumentListener(recalc);
        gstField.getDocument().addDocumentListener(recalc);
        discountType.addActionListener(e -> {
            discountField.setEnabled(discountType.getSelectedItem() != DiscountType.NONE && !paidMode());
            recalculate();
        });
        payButton.addActionListener(e -> onPay());
        receiptButton.addActionListener(e -> onReceipt());
    }

    // ---------- loading data ----------

    private void refreshList() {
        if (paidMode()) {
            Background.run(service::getRecentBills, loaded -> {
                listNotice.setVisible(false);
                bills.clear();
                bills.addAll(loaded);
                fillBillList();
            }, this::listFailed);
        } else {
            Background.run(service::getOrdersReadyForBilling, loaded -> {
                listNotice.setVisible(false);
                orders.clear();
                orders.addAll(loaded);
                boolean stillThere = orders.stream().anyMatch(o -> o.getOrderId() == selectedOrderId);
                fillOrderList();
                if (selectedOrderId != 0 && !stillThere) {
                    selectedOrderId = 0; // it was paid (or changed) elsewhere
                    lines.clear();
                    fillItems();
                    showNothing();
                } else if (selectedOrderId != 0) {
                    loadOrderLines(); // totals may have changed
                }
            }, this::listFailed);
        }
    }

    private void listFailed(Exception error) {
        orders.clear();
        bills.clear();
        filling = true;
        listModel.setRowCount(0);
        filling = false;
        listCount.setText("0");
        listNotice.setText(Messages.notice(error));
        listNotice.setToolTipText(error.getMessage());
        listNotice.setVisible(true);
        showNothing();
    }

    private void loadOrderLines() {
        int wanted = selectedOrderId;
        Background.run(() -> service.getLines(wanted), loaded -> {
            if (wanted != selectedOrderId) {
                return; // the user clicked another order meanwhile
            }
            lines.clear();
            lines.addAll(loaded);
            fillItems();
            showSelectedOrder();
        }, error -> {
            lines.clear();
            fillItems();
            messageLabel.setText("Could not load the food items.");
            Messages.error(this, error);
        });
    }

    private void loadPaidBill(int billId) {
        Background.run(() -> service.getBill(billId), bill -> {
            if (billId != selectedBillId || bill == null) {
                return;
            }
            shownBill = bill;
            lines.clear();
            lines.addAll(bill.getLines());
            fillItems();
            showPaidBill(bill);
        }, error -> Messages.error(this, error));
    }

    // ---------- drawing data into the screen ----------

    private void fillOrderList() {
        filling = true;
        listModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < orders.size(); i++) {
            OrderSummary o = orders.get(i);
            listModel.addRow(new Object[]{"#" + o.getOrderId(), "T" + o.getTableNumber(), o.getCustomerName(),
                    o.getItemCount(), CURRENCY.format(o.getTotal()), o.getStatus().name()});
            if (o.getOrderId() == selectedOrderId) {
                select = i;
            }
        }
        if (select >= 0) {
            DataTableStyle.selectModelRow(listTable, select);
        } else {
            listTable.clearSelection();
        }
        filling = false;
        listCount.setText(orders.size() == 1 ? "1 order served" : orders.size() + " orders served");
    }

    private void fillBillList() {
        filling = true;
        listModel.setRowCount(0);
        int select = -1;
        for (int i = 0; i < bills.size(); i++) {
            Bill b = bills.get(i);
            listModel.addRow(new Object[]{"#" + b.getBillId(), "T" + b.getTableNumber(), b.getCustomerName(),
                    WHEN.format(b.getPaidAt()), CURRENCY.format(b.getCalculation().getGrandTotal()),
                    b.getPaymentMethod().getLabel()});
            if (b.getBillId() == selectedBillId) {
                select = i;
            }
        }
        if (select >= 0) {
            DataTableStyle.selectModelRow(listTable, select);
        } else {
            listTable.clearSelection();
        }
        filling = false;
        listCount.setText(bills.size() == 1 ? "1 bill" : bills.size() + " bills");
    }

    private void fillItems() {
        itemsModel.setRowCount(0);
        for (OrderLine l : lines) {
            itemsModel.addRow(new Object[]{l.getName(), l.getQuantity(), CURRENCY.format(l.getUnitPrice()),
                    CURRENCY.format(l.getSubtotal())});
        }
    }

    private OrderSummary selectedOrder() {
        return orders.stream().filter(o -> o.getOrderId() == selectedOrderId).findFirst().orElse(null);
    }

    /** Nothing selected: empty bill card, everything disabled. */
    private void showNothing() {
        billTitle.setText(paidMode() ? "No bill selected" : "No order selected");
        billInfo.setText(paidMode() ? "Click a bill in the list to see it" : "Only SERVED orders can be billed. Click one in the list");
        billBadge.setStatus("-", Theme.TEXT_MUTED);
        current = null;
        shownBill = null;
        setInputsEnabled(false);
        clearBreakdown();
        messageLabel.setText(" ");
        applyButtonState();
    }

    /** A SERVED order is selected: inputs are open and the bill is calculated from what is typed. */
    private void showSelectedOrder() {
        OrderSummary o = selectedOrder();
        if (o == null) {
            showNothing();
            return;
        }
        billTitle.setText("Order #" + o.getOrderId() + "  -  Table " + o.getTableNumber());
        String phone = o.getCustomerPhone() == null ? "" : "  |  " + o.getCustomerPhone();
        billInfo.setText(o.getCustomerName() + phone);
        billBadge.setStatus(o.getStatus().name(), Theme.statusColor(o.getStatus().name()));
        setInputsEnabled(true);
        recalculate();
    }

    /** A saved bill is selected: show exactly what was paid, nothing can be edited. */
    private void showPaidBill(Bill b) {
        billTitle.setText("Bill #" + b.getBillId() + "  -  Order #" + b.getOrderId());
        billInfo.setText("Table " + b.getTableNumber() + "  |  " + b.getCustomerName() + "  |  " + WHEN.format(b.getPaidAt()));
        billBadge.setStatus(b.getPaymentStatus(), Theme.statusColor(b.getPaymentStatus()));
        setInputsEnabled(false);
        BillCalculation c = b.getCalculation();
        filling = true;
        discountType.setSelectedItem(c.getDiscountType());
        discountField.setText(c.getDiscountValue().toPlainString());
        gstField.setText(c.getGstPercent().toPlainString());
        methodBox.setSelectedItem(b.getPaymentMethod());
        filling = false;
        current = c;
        showBreakdown(c);
        messageLabel.setText(" ");
        applyButtonState();
    }

    private void setInputsEnabled(boolean enabled) {
        discountType.setEnabled(enabled);
        discountField.setEnabled(enabled && discountType.getSelectedItem() != DiscountType.NONE);
        gstField.setEnabled(enabled);
        methodBox.setEnabled(enabled);
    }

    private void clearBreakdown() {
        for (JLabel l : new JLabel[]{subtotalValue, discountValue, taxableValue, gstValue}) {
            l.setText("–");
        }
        gstLabel.setText("GST");
        totalValue.setText("–");
    }

    private void showBreakdown(BillCalculation c) {
        subtotalValue.setText(CURRENCY.format(c.getSubtotal()));
        String d = c.getDiscountType() == DiscountType.PERCENT
                ? " (" + c.getDiscountValue().stripTrailingZeros().toPlainString() + "%)" : "";
        discountValue.setText("-" + CURRENCY.format(c.getDiscountAmount()) + d);
        taxableValue.setText(CURRENCY.format(c.getTaxableAmount()));
        gstLabel.setText("GST (" + c.getGstPercent().stripTrailingZeros().toPlainString() + "%)");
        gstValue.setText(CURRENCY.format(c.getGstAmount()));
        totalValue.setText(CURRENCY.format(c.getGrandTotal()));
    }

    /** Calculates the bill from the typed discount and GST (ready mode). Invalid values show a clear message. */
    private void recalculate() {
        if (filling || paidMode() || selectedOrder() == null) {
            return;
        }
        try {
            current = service.calculate(lines, (DiscountType) discountType.getSelectedItem(),
                    discountField.getText(), gstField.getText());
            showBreakdown(current);
            messageLabel.setText(" ");
        } catch (InvalidInputException e) {
            current = null;
            clearBreakdown();
            messageLabel.setText("<html><body style='width:330px'>" + e.getMessage() + "</body></html>");
        }
        applyButtonState();
    }

    private void applyButtonState() {
        boolean ready = !paidMode() && selectedOrder() != null && current != null && !lines.isEmpty() && !busy;
        payButton.setEnabled(ready);
        receiptButton.setEnabled(paidMode() && shownBill != null && !busy);
        receiptButton.setVisible(paidMode());
        payButton.setVisible(!paidMode());
    }

    // ---------- button actions ----------

    private void onPay() {
        OrderSummary order = selectedOrder();
        BillCalculation bill = current;
        PaymentMethod method = (PaymentMethod) methodBox.getSelectedItem();
        if (order == null || bill == null) {
            return;
        }
        if (method == null) {
            Messages.error(this, new InvalidInputException("Please choose a payment method (Cash, UPI or Card)."));
            return;
        }
        boolean yes = Messages.confirm(this, "Confirm payment",
                "Take payment of " + CURRENCY.format(bill.getGrandTotal()) + " by " + method.getLabel() + "\n"
                        + "for Order #" + order.getOrderId() + " (Table " + order.getTableNumber() + ")?\n\n"
                        + "After the payment is saved, the table becomes available again.");
        if (!yes) {
            return; // payment cancelled: nothing was saved, the table stays occupied
        }
        busy = true;
        payButton.setText("Processing...");
        applyButtonState();
        Background.run(() -> service.pay(order, bill, method), saved -> {
            busy = false;
            payButton.setText("Confirm Payment");
            Messages.info(this, "Payment successful",
                    "Payment of " + CURRENCY.format(saved.getCalculation().getGrandTotal()) + " received ("
                            + saved.getPaymentMethod().getLabel() + ").\nBill number " + saved.getBillId()
                            + ". Table " + saved.getTableNumber() + " is now available.");
            selectedOrderId = 0;
            lines.clear();
            fillItems();
            showNothing();
            refreshList();
            ReceiptDialog.show(this, "Receipt - Bill #" + saved.getBillId(), service.buildReceipt(saved));
        }, error -> {
            busy = false;
            payButton.setText("Confirm Payment");
            Messages.error(this, error); // nothing was saved; the table stays occupied
            refreshList();
        });
    }

    private void onReceipt() {
        if (shownBill != null) {
            ReceiptDialog.show(this, "Receipt - Bill #" + shownBill.getBillId(), service.buildReceipt(shownBill));
        }
    }
}

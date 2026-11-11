package com.restaurant.service;

import com.restaurant.dao.BillDao;
import com.restaurant.dao.OrderDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.Bill;
import com.restaurant.model.BillCalculation;
import com.restaurant.model.DiscountType;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Business rules for billing: which orders can be billed, how a bill is calculated (discount, GST, total), what the
 * receipt text looks like, and the payment itself. No Swing code and no SQL here. The money formula lives in
 * {@link BillCalculation}; the database re-checks everything inside the payment transaction.
 */
public class BillingService {

    /**
     * The GST percentage the Billing screen starts with. The ONLY place where it is written down: change it here
     * (or type another value in the GST field of the Billing screen, 0 to 100) - nothing else needs to change.
     */
    public static final BigDecimal DEFAULT_GST_PERCENT = new BigDecimal("5.00");

    /** How many paid bills the "Paid bills" list shows. */
    public static final int RECENT_BILLS = 50;

    private static final String NUMBER_PATTERN = "\\d{1,8}(\\.\\d{1,2})?";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final int RECEIPT_WIDTH = 46;

    private final BillDao billDao;
    private final OrderDao orderDao;

    public BillingService(BillDao billDao, OrderDao orderDao) {
        this.billDao = billDao;
        this.orderDao = orderDao;
    }

    // ---------- reading ----------

    public List<OrderSummary> getOrdersReadyForBilling() throws DatabaseException {
        return billDao.findOrdersReadyForBilling();
    }

    public List<OrderLine> getLines(int orderId) throws DatabaseException {
        return orderId <= 0 ? List.of() : orderDao.findLines(orderId);
    }

    public List<Bill> getRecentBills() throws DatabaseException {
        return billDao.findRecentBills(RECENT_BILLS);
    }

    public Bill getBill(int billId) throws DatabaseException {
        return billDao.findBill(billId);
    }

    // ---------- calculating ----------

    /** Order subtotal = sum of (price x quantity). Stream API with map and reduce. */
    public BigDecimal subtotalOf(List<OrderLine> lines) {
        return lines.stream().map(OrderLine::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Calculates the bill from what is typed on the screen. Empty discount text counts as 0.
     * Throws InvalidInputException with a clear message for anything that is not a valid discount or GST value.
     */
    public BillCalculation calculate(List<OrderLine> lines, DiscountType type, String discountText, String gstText)
            throws InvalidInputException {
        BigDecimal gst = parseNumber(gstText, "GST percentage", DEFAULT_GST_PERCENT);
        BigDecimal discount = type == null || type == DiscountType.NONE
                ? BigDecimal.ZERO
                : parseNumber(discountText, "discount", BigDecimal.ZERO);
        return BillCalculation.calculate(subtotalOf(lines), type, discount, gst);
    }

    private static BigDecimal parseNumber(String text, String what, BigDecimal whenEmpty) throws InvalidInputException {
        String t = text == null ? "" : text.trim().replace(",", "");
        if (t.isEmpty()) {
            return whenEmpty;
        }
        if (t.startsWith("-")) {
            throw new InvalidInputException("The " + what + " cannot be negative.");
        }
        if (!t.matches(NUMBER_PATTERN)) {
            throw new InvalidInputException("Please enter the " + what + " as a number such as 10 or 12.50 "
                    + "(at most 2 digits after the dot). You typed: " + t);
        }
        return new BigDecimal(t);
    }

    // ---------- paying ----------

    /**
     * Records the payment of a SERVED order and (inside the same transaction) frees its table.
     * {@code bill} is the calculation the cashier saw and confirmed; if the order changed meanwhile the DAO refuses.
     * Returns the saved bill for the receipt.
     */
    public Bill pay(OrderSummary order, BillCalculation bill, PaymentMethod method)
            throws InvalidInputException, DatabaseException {
        if (order == null) {
            throw new InvalidInputException("Please click an order in the list first.");
        }
        if (order.getStatus() != OrderStatus.SERVED) {
            throw new InvalidInputException("Only SERVED orders can be billed. This order is "
                    + order.getStatus().getLabel().toUpperCase() + ".");
        }
        if (bill == null) {
            throw new InvalidInputException("The bill could not be calculated. Please check the discount and GST values.");
        }
        if (method == null) {
            throw new InvalidInputException("Please choose a payment method (Cash, UPI or Card).");
        }
        int billId = billDao.payOrder(order.getOrderId(), bill, method);
        Bill saved = billDao.findBill(billId);
        if (saved == null) {
            throw new DatabaseException("The payment was saved (bill number " + billId + ") but the receipt could not be loaded.");
        }
        return saved;
    }

    // ---------- receipt ----------

    /** The receipt as plain text for a monospaced font. Shown on screen and printed with Java's own print support. */
    public String buildReceipt(Bill bill) {
        BillCalculation c = bill.getCalculation();
        StringBuilder sb = new StringBuilder();
        String line = "-".repeat(RECEIPT_WIDTH);
        sb.append(center("RestoDesk")).append('\n');
        sb.append(center("Restaurant Bill")).append('\n');
        sb.append(line).append('\n');
        sb.append(String.format("Bill No   : %d%n", bill.getBillId()));
        sb.append(String.format("Order No  : %d%n", bill.getOrderId()));
        sb.append(String.format("Table     : %d%n", bill.getTableNumber()));
        sb.append(String.format("Customer  : %s%n", bill.getCustomerName()));
        sb.append(String.format("Date/Time : %s%n", WHEN.format(bill.getPaidAt())));
        sb.append(line).append('\n');
        sb.append(String.format("%-20s %3s %9s %10s%n", "Item", "Qty", "Price", "Amount"));
        for (OrderLine l : bill.getLines()) {
            String name = l.getName().length() > 20 ? l.getName().substring(0, 19) + "." : l.getName();
            sb.append(String.format("%-20s %3d %9s %10s%n", name, l.getQuantity(),
                    money(l.getUnitPrice()), money(l.getSubtotal())));
        }
        sb.append(line).append('\n');
        sb.append(row("Subtotal", money(c.getSubtotal())));
        String discountLabel = switch (c.getDiscountType()) {
            case PERCENT -> "Discount (" + c.getDiscountValue().stripTrailingZeros().toPlainString() + "%)";
            case FIXED -> "Discount (fixed)";
            default -> "Discount";
        };
        sb.append(row(discountLabel, "-" + money(c.getDiscountAmount())));
        sb.append(row("Taxable amount", money(c.getTaxableAmount())));
        sb.append(row("GST (" + c.getGstPercent().stripTrailingZeros().toPlainString() + "%)", money(c.getGstAmount())));
        sb.append(line).append('\n');
        sb.append(row("GRAND TOTAL", money(c.getGrandTotal())));
        sb.append(line).append('\n');
        sb.append(String.format("Payment method : %s%n", bill.getPaymentMethod().getLabel()));
        sb.append(String.format("Payment status : %s%n", bill.getPaymentStatus()));
        sb.append(line).append('\n');
        sb.append(center("Thank you! Please visit again.")).append('\n');
        return sb.toString();
    }

    /** "₹1,234.50" - Indian rupee sign, comma every 3 digits, always 2 decimals. */
    public static String money(BigDecimal amount) {
        return String.format(Locale.ENGLISH, "₹%,.2f", amount);
    }

    private static String row(String label, String value) {
        int gap = Math.max(1, RECEIPT_WIDTH - label.length() - value.length());
        return label + " ".repeat(gap) + value + "\n";
    }

    private static String center(String text) {
        int pad = Math.max(0, (RECEIPT_WIDTH - text.length()) / 2);
        return " ".repeat(pad) + text;
    }
}

package com.restaurant.model;

import com.restaurant.exception.InvalidInputException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The money part of a bill. Immutable: once calculated, the numbers never change.
 * <pre>
 *   subtotal   = sum of (quantity x price) of every item          (given)
 *   discount   = percent of subtotal, or a fixed amount            (never more than the subtotal)
 *   taxable    = subtotal - discount
 *   gst        = taxable x gst% / 100
 *   grand total= taxable + gst
 * </pre>
 * All money is {@link BigDecimal} with 2 decimals and HALF_UP rounding (the usual rounding for rupees and paise),
 * so there are no floating-point errors such as 0.1 + 0.2 = 0.30000000000000004.
 * This class has no Swing and no SQL, so the screen, the service and the database layer all use the SAME formula.
 */
public final class BillCalculation {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MAX_GST_PERCENT = BigDecimal.valueOf(100);

    private final BigDecimal subtotal;
    private final DiscountType discountType;
    private final BigDecimal discountValue;
    private final BigDecimal discountAmount;
    private final BigDecimal taxableAmount;
    private final BigDecimal gstPercent;
    private final BigDecimal gstAmount;
    private final BigDecimal grandTotal;

    private BillCalculation(BigDecimal subtotal, DiscountType discountType, BigDecimal discountValue,
                            BigDecimal discountAmount, BigDecimal gstPercent) {
        this.subtotal = subtotal;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.discountAmount = discountAmount;
        this.taxableAmount = subtotal.subtract(discountAmount);
        this.gstPercent = gstPercent;
        this.gstAmount = taxableAmount.multiply(gstPercent).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        this.grandTotal = taxableAmount.add(gstAmount);
    }

    /**
     * Calculates a bill. Refuses (InvalidInputException with a ready-to-show message) a discount that is negative,
     * a percentage above 100, a fixed discount above the subtotal, or a GST percentage outside 0 to 100.
     * With {@link DiscountType#NONE} the discount value is ignored.
     */
    public static BillCalculation calculate(BigDecimal subtotal, DiscountType type, BigDecimal discountValue,
                                            BigDecimal gstPercent) throws InvalidInputException {
        if (subtotal == null || subtotal.signum() <= 0) {
            throw new InvalidInputException("There is nothing to bill: the order has no food items.");
        }
        if (gstPercent == null || gstPercent.signum() < 0 || gstPercent.compareTo(MAX_GST_PERCENT) > 0) {
            throw new InvalidInputException("The GST percentage must be between 0 and 100.");
        }
        BigDecimal sub = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal value = BigDecimal.ZERO.setScale(2);
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        DiscountType kind = type == null ? DiscountType.NONE : type;
        if (kind != DiscountType.NONE) {
            if (discountValue == null || discountValue.signum() < 0) {
                throw new InvalidInputException("The discount cannot be negative.");
            }
            value = discountValue.setScale(2, RoundingMode.HALF_UP);
            if (kind == DiscountType.PERCENT) {
                if (value.compareTo(HUNDRED) > 0) {
                    throw new InvalidInputException("A percentage discount must be between 0 and 100.");
                }
                discount = sub.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            } else {
                if (value.compareTo(sub) > 0) {
                    throw new InvalidInputException("A fixed discount cannot be more than the subtotal ("
                            + sub.toPlainString() + ").");
                }
                discount = value;
            }
        }
        return new BillCalculation(sub, kind, value, discount, gstPercent.setScale(2, RoundingMode.HALF_UP));
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    /** What the cashier typed: 10 for "10 percent", 50.00 for "Rs. 50 off". */
    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    /** The discount in rupees. */
    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public BigDecimal getTaxableAmount() {
        return taxableAmount;
    }

    public BigDecimal getGstPercent() {
        return gstPercent;
    }

    public BigDecimal getGstAmount() {
        return gstAmount;
    }

    public BigDecimal getGrandTotal() {
        return grandTotal;
    }

    /** Rebuilds the numbers of a bill that is already saved (no validation: the database holds what was paid). */
    public static BillCalculation fromSaved(BigDecimal subtotal, DiscountType type, BigDecimal discountValue,
                                            BigDecimal discountAmount, BigDecimal gstPercent) {
        return new BillCalculation(subtotal.setScale(2, RoundingMode.HALF_UP), type,
                discountValue.setScale(2, RoundingMode.HALF_UP), discountAmount.setScale(2, RoundingMode.HALF_UP),
                gstPercent.setScale(2, RoundingMode.HALF_UP));
    }
}

package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.Bill;
import com.restaurant.model.BillCalculation;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.PaymentMethod;

import java.util.List;

/**
 * Everything the program may ask the database about bills and payments.
 * <p>
 * BUSINESS RULE: a table is released ONLY inside {@link #payOrder}, in the same transaction that saves the payment.
 * Showing a bill, choosing a payment method or cancelling the dialog never touches the database.
 */
public interface BillDao {

    /** Orders that can be billed now: status SERVED, oldest first (with table, customer, items and total). */
    List<OrderSummary> findOrdersReadyForBilling() throws DatabaseException;

    /** The most recent paid bills (newest first), without their food items. */
    List<Bill> findRecentBills(int limit) throws DatabaseException;

    /** One saved bill with its food items, or null if it does not exist. */
    Bill findBill(int billId) throws DatabaseException;

    /**
     * Records the payment in ONE transaction and returns the new bill id:
     * lock the order, check it is SERVED and not paid yet, re-read its items and re-calculate the bill
     * (it must equal {@code expected}, otherwise the order was changed meanwhile), insert the bill (PAID),
     * set the order to PAID and set its table AVAILABLE (unless another open order uses the table).
     * Any failure rolls everything back: no bill, order still SERVED, table still OCCUPIED.
     */
    int payOrder(int orderId, BillCalculation expected, PaymentMethod method)
            throws DatabaseException, InvalidInputException;
}

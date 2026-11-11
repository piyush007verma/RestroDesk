package com.restaurant.service;

import com.restaurant.dao.HistoryDao;
import com.restaurant.dao.OrderDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.Bill;
import com.restaurant.model.HistoryDetails;
import com.restaurant.model.HistoryFilter;
import com.restaurant.model.HistoryRow;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.PaymentMethod;

import java.time.LocalDate;
import java.util.List;

/**
 * Order History rules: checks what the user typed into the filters, then asks the DAO. Searching is read-only.
 * The only change this class can make is {@link #clearHistory()}, which hides finished orders from the History list by
 * setting a time stamp; it cannot delete or edit an order, a bill or a payment.
 */
public class HistoryService {

    /** The list never shows more rows than this (newest first); the screen tells the user when it is cut. */
    public static final int MAX_ROWS = 500;

    private final HistoryDao historyDao;
    private final BillingService billingService;
    private final OrderDao orderDao;

    public HistoryService(HistoryDao historyDao, BillingService billingService, OrderDao orderDao) {
        this.historyDao = historyDao;
        this.billingService = billingService;
        this.orderDao = orderDao;
    }

    /** Turns the raw screen input into a filter. Empty text = no filter. Throws a friendly message on bad input. */
    public HistoryFilter buildFilter(String orderIdText, String tableText, OrderStatus status, String paymentStatus,
                                     PaymentMethod method, LocalDate from, LocalDate to) throws InvalidInputException {
        Integer orderId = parsePositive(orderIdText, "Order ID");
        Integer table = parsePositive(tableText, "Table number");
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidInputException("The start date is after the end date. Please choose the start date first, then a later end date.");
        }
        return new HistoryFilter(orderId, table, status, paymentStatus, method, from, to);
    }

    public List<HistoryRow> search(HistoryFilter filter) throws DatabaseException {
        return historyDao.search(filter, MAX_ROWS);
    }

    /** Full details of one order: its food items and, when it was billed, the saved bill. */
    public HistoryDetails getDetails(HistoryRow row) throws DatabaseException {
        Bill bill = row.hasBill() ? billingService.getBill(row.billId()) : null;
        List<OrderLine> lines = bill != null ? bill.getLines() : orderDao.findLines(row.orderId());
        return new HistoryDetails(row, lines, bill);
    }

    /** True when migration 003 was run (the Clear Order History button can work). */
    public boolean isClearingAvailable() throws DatabaseException {
        return historyDao.isClearingAvailable();
    }

    /** How many finished orders a clear would hide right now (0 = nothing to clear). */
    public int countClearableOrders() throws DatabaseException {
        return historyDao.countClearableOrders();
    }

    /**
     * Hides all finished (PAID / CANCELLED) orders from Order History. Bills, payments, GST, discounts, order items and
     * reports are not touched (see HistoryDaoImpl). Returns how many orders were hidden; 0 when there was nothing to clear.
     */
    public int clearHistory() throws DatabaseException {
        return historyDao.clearClosedOrders();
    }

    /** The saved receipt text (same text as the Billing screen), or null when the order has no bill. */
    public String receiptOf(HistoryDetails details) {
        return details.bill() == null ? null : billingService.buildReceipt(details.bill());
    }

    private static Integer parsePositive(String text, String what) throws InvalidInputException {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (!t.matches("\\d{1,9}") || Integer.parseInt(t) < 1) {
            throw new InvalidInputException(what + " must be a whole number such as 12 (digits only).");
        }
        return Integer.valueOf(t);
    }
}

package com.restaurant.model;

import java.time.LocalDateTime;
import java.util.List;

/** A saved (paid) bill with everything needed for the receipt. Read-only: built from the database. */
public class Bill {

    private final int billId;
    private final int orderId;
    private final int tableNumber;
    private final String customerName;
    private final LocalDateTime paidAt;
    private final PaymentMethod paymentMethod;
    private final String paymentStatus;
    private final BillCalculation calculation;
    private final List<OrderLine> lines;

    public Bill(int billId, int orderId, int tableNumber, String customerName, LocalDateTime paidAt,
                PaymentMethod paymentMethod, String paymentStatus, BillCalculation calculation, List<OrderLine> lines) {
        this.billId = billId;
        this.orderId = orderId;
        this.tableNumber = tableNumber;
        this.customerName = customerName;
        this.paidAt = paidAt;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = paymentStatus;
        this.calculation = calculation;
        this.lines = List.copyOf(lines);
    }

    public int getBillId() {
        return billId;
    }

    public int getOrderId() {
        return orderId;
    }

    public int getTableNumber() {
        return tableNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    /** "PAID" (a bill row is only written when payment succeeded). */
    public String getPaymentStatus() {
        return paymentStatus;
    }

    public BillCalculation getCalculation() {
        return calculation;
    }

    public List<OrderLine> getLines() {
        return lines;
    }
}

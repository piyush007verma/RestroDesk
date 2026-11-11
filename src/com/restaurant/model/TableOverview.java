package com.restaurant.model;

/**
 * One table plus, when it is occupied, who is sitting there and which order is open.
 * This is what the Table Management screen shows. The customer fields come from the
 * table's open order (PENDING, PREPARING, READY or SERVED) and are empty for free tables.
 */
public class TableOverview {

    private final int id;
    private final int number;
    private final int capacity;
    private final TableStatus status;
    private final int openOrderId;        // 0 when there is no open order
    private final String customerName;    // null when there is no open order
    private final String customerPhone;   // null when not given

    public TableOverview(int id, int number, int capacity, TableStatus status,
                         int openOrderId, String customerName, String customerPhone) {
        this.id = id;
        this.number = number;
        this.capacity = capacity;
        this.status = status;
        this.openOrderId = openOrderId;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
    }

    public int getId() {
        return id;
    }

    public int getNumber() {
        return number;
    }

    public int getCapacity() {
        return capacity;
    }

    public TableStatus getStatus() {
        return status;
    }

    public int getOpenOrderId() {
        return openOrderId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }
}

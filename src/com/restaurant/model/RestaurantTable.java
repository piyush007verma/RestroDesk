package com.restaurant.model;

/** One physical table in the restaurant (maps to the restaurant_tables DB table). */
public class RestaurantTable {

    private final int id;
    private final int number;
    private final int capacity;
    private TableStatus status;

    public RestaurantTable(int id, int number, int capacity, TableStatus status) {
        this.id = id;
        this.number = number;
        this.capacity = capacity;
        this.status = status;
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

    public void setStatus(TableStatus status) {
        this.status = status;
    }
}

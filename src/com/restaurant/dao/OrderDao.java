package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.TableOverview;

import java.util.List;

/**
 * Everything the program may ask the database about orders and the food inside them.
 * <p>
 * IMPORTANT RULE (decided in Milestone 2): seating a customer on the Tables screen ALREADY creates the order
 * (status PENDING). Order Management only works with that order: it adds food to it and moves it through the
 * kitchen statuses. It never creates a second order for a table that already has an open one.
 * <p>
 * "Open order" means an order that is not PAID or CANCELLED. Only open orders can be changed.
 * InvalidInputException carries a ready-to-show message when the database state forbids a change
 * (the order was paid meanwhile, the dish is unavailable, the quantity would drop below 1, ...).
 */
public interface OrderDao {

    /** All open orders with table number, customer, time, number of items and total, oldest first. */
    List<OrderSummary> findActiveOrders() throws DatabaseException;

    /** The food items of one order, in the order they were first added. */
    List<OrderLine> findLines(int orderId) throws DatabaseException;

    /** Occupied tables that have no open order (should be rare, e.g. an order was cancelled by hand). */
    List<TableOverview> findSeatedTablesWithoutOrder() throws DatabaseException;

    /**
     * Returns the id of the open order of an OCCUPIED table. If the table already has one it is REUSED;
     * only when there is none, a new PENDING order is created. Never creates a duplicate.
     */
    int findOrCreateOpenOrder(int tableId, String customerName) throws DatabaseException, InvalidInputException;

    /**
     * Adds {@code quantity} of a dish to an open order: a new line, or more of a line that already exists.
     * The price is copied from the menu when the line is created. Refuses unavailable dishes.
     */
    void addItem(int orderId, int itemId, int quantity) throws DatabaseException, InvalidInputException;

    /** Changes the quantity of an existing line by {@code delta} (for example +1 or -1). The result must stay 1 to 99. */
    void changeQuantity(int orderId, int itemId, int delta) throws DatabaseException, InvalidInputException;

    /** Takes a dish off the order completely. */
    void removeItem(int orderId, int itemId) throws DatabaseException, InvalidInputException;

    /**
     * Changes the status from {@code expectedCurrent} to {@code target}. Fails if somebody else already changed it,
     * and refuses to send an order with no food to the kitchen.
     */
    void updateStatus(int orderId, OrderStatus expectedCurrent, OrderStatus target)
            throws DatabaseException, InvalidInputException;
}

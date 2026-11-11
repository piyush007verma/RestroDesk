package com.restaurant.service;

import com.restaurant.dao.OrderDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MenuItem;
import com.restaurant.model.OrderLine;
import com.restaurant.model.OrderStatus;
import com.restaurant.model.OrderSummary;
import com.restaurant.model.TableOverview;

import java.math.BigDecimal;
import java.util.List;

/**
 * Business rules for orders: what may be added, how quantities change, how the status moves and how totals are
 * calculated. The screen passes what the user clicked; this class checks it and only then calls the DAO.
 * No Swing code and no SQL here. The rules that depend on the database at that exact moment (is the order still
 * open? is the dish still available? is the order empty?) are checked again inside the DAO's transaction.
 */
public class OrderService {

    private static final int MIN_NAME_LENGTH = 2;
    private static final int MAX_NAME_LENGTH = 100; // orders.customer_name is VARCHAR(100)

    private final OrderDao dao;

    public OrderService(OrderDao dao) {
        this.dao = dao;
    }

    // ---------- reading and calculating ----------

    public List<OrderSummary> getActiveOrders() throws DatabaseException {
        return dao.findActiveOrders();
    }

    public List<OrderLine> getLines(int orderId) throws DatabaseException {
        return orderId <= 0 ? List.of() : dao.findLines(orderId);
    }

    /** Order total = sum of (price x quantity) of every line. Stream API with map and reduce. */
    public BigDecimal totalOf(List<OrderLine> lines) {
        return lines.stream()
                .map(OrderLine::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Number of items on the order: the quantities added up (2 burgers + 1 tea = 3). */
    public int itemCountOf(List<OrderLine> lines) {
        return lines.stream().mapToInt(OrderLine::getQuantity).sum();
    }

    // ---------- food items ----------

    public void addItem(int orderId, MenuItem item, int quantity) throws InvalidInputException, DatabaseException {
        requireOrder(orderId);
        if (item == null) {
            throw new InvalidInputException("Please click a food item in the menu first.");
        }
        if (!item.isAvailable()) {
            throw new InvalidInputException("\"" + item.getName() + "\" is not available right now, "
                    + "so it cannot be added to an order.");
        }
        if (quantity < 1) {
            throw new InvalidInputException("The quantity must be at least 1.");
        }
        if (quantity > OrderLine.MAX_QUANTITY) {
            throw new InvalidInputException("You can add at most " + OrderLine.MAX_QUANTITY + " of one item at a time.");
        }
        dao.addItem(orderId, item.getId(), quantity);
    }

    public void increaseQuantity(int orderId, int itemId) throws InvalidInputException, DatabaseException {
        requireOrder(orderId);
        requireLine(itemId);
        dao.changeQuantity(orderId, itemId, +1);
    }

    /** Never goes below 1: the DAO refuses with a clear message and the user is told to use Remove. */
    public void decreaseQuantity(int orderId, int itemId) throws InvalidInputException, DatabaseException {
        requireOrder(orderId);
        requireLine(itemId);
        dao.changeQuantity(orderId, itemId, -1);
    }

    public void removeItem(int orderId, int itemId) throws InvalidInputException, DatabaseException {
        requireOrder(orderId);
        requireLine(itemId);
        dao.removeItem(orderId, itemId);
    }

    // ---------- status ----------

    /**
     * Moves an order ONE step forward (PENDING -> PREPARING -> READY -> SERVED) or one step back (to fix a mistake).
     * Skipping steps is not allowed, and PAID / CANCELLED are not set on this screen.
     */
    public void changeStatus(OrderSummary order, OrderStatus target) throws InvalidInputException, DatabaseException {
        if (order == null) {
            throw new InvalidInputException("Please click an order in the list first.");
        }
        OrderStatus current = order.getStatus();
        if (!current.isOpen()) {
            throw new InvalidInputException("This order is already " + current.getLabel().toLowerCase()
                    + " and cannot be changed.");
        }
        if (target == null || (target != current.next() && target != current.previous())) {
            throw new InvalidInputException("An order cannot go from " + current.getLabel().toUpperCase() + " to "
                    + (target == null ? "nothing" : target.getLabel().toUpperCase())
                    + " directly. Move it one step at a time.");
        }
        dao.updateStatus(order.getOrderId(), current, target);
    }

    // ---------- orders for seated tables ----------

    public List<TableOverview> getSeatedTablesWithoutOrder() throws DatabaseException {
        return dao.findSeatedTablesWithoutOrder();
    }

    /**
     * For an occupied table that has no open order (a repair case): reuses its open order if one exists,
     * otherwise creates ONE PENDING order. Returns the order id.
     */
    public int openOrderForTable(int tableId, String customerName) throws InvalidInputException, DatabaseException {
        if (tableId <= 0) {
            throw new InvalidInputException("Please choose a table first.");
        }
        String name = customerName == null ? "" : customerName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new InvalidInputException("Please enter the customer's name.");
        }
        if (name.length() < MIN_NAME_LENGTH) {
            throw new InvalidInputException("The customer's name must have at least " + MIN_NAME_LENGTH + " letters.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("The name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }
        return dao.findOrCreateOpenOrder(tableId, name);
    }

    // ---------- small checks ----------

    private static void requireOrder(int orderId) throws InvalidInputException {
        if (orderId <= 0) {
            throw new InvalidInputException("Please click an order in the list first.");
        }
    }

    private static void requireLine(int itemId) throws InvalidInputException {
        if (itemId <= 0) {
            throw new InvalidInputException("Please click a food item in the order first.");
        }
    }
}

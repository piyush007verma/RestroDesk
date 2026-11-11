package com.restaurant.model;

/**
 * The life of an order, in order: PENDING (just seated) -> PREPARING (kitchen is cooking) -> READY (food is ready)
 * -> SERVED (on the table), and finally PAID (Billing) or CANCELLED.
 * <p>
 * The names are exactly the values of the ENUM column orders.status in MySQL.
 * An order is "open" while it is not PAID or CANCELLED; only open orders can be changed.
 */
public enum OrderStatus {
    PENDING("Pending"),
    PREPARING("Preparing"),
    READY("Ready"),
    SERVED("Served"),
    PAID("Paid"),
    CANCELLED("Cancelled");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** true while the order is still being worked on (everything except PAID and CANCELLED). */
    public boolean isOpen() {
        return this != PAID && this != CANCELLED;
    }

    /** The next step of the kitchen flow, or null when there is none (SERVED hands over to Billing). */
    public OrderStatus next() {
        return switch (this) {
            case PENDING -> PREPARING;
            case PREPARING -> READY;
            case READY -> SERVED;
            default -> null;
        };
    }

    /** The step before this one (to correct a mistake), or null for PENDING and the finished statuses. */
    public OrderStatus previous() {
        return switch (this) {
            case PREPARING -> PENDING;
            case READY -> PREPARING;
            case SERVED -> READY;
            default -> null;
        };
    }

    /**
     * Text for SQL: the finished statuses, as in {@code status NOT IN (<this>)}.
     * "Open" is written as "not finished" on purpose, so the query also works on a database that does not
     * know READY yet and keeps working if a status is added later. The text is a constant, never user input.
     */
    public static String closedSqlList() {
        return "'" + PAID.name() + "', '" + CANCELLED.name() + "'";
    }
}

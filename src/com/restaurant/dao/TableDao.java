package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.TableUnavailableException;
import com.restaurant.model.TableOverview;

import java.util.List;

/**
 * Database operations for restaurant tables.
 * <p>
 * DATABASE RULE (decided in Milestone 2, no schema change needed):
 * assigning a table = creating ONE order with status PENDING for that customer on that table
 * AND setting the table to OCCUPIED, both in a single transaction.
 * The Order Management milestone must ADD food items to that existing PENDING order
 * (look it up with {@link #findAllWithCustomers()} / orders.table_id) and never create a second order.
 */
public interface TableDao {

    /** Every table with the customer of its open order, sorted by table number. */
    List<TableOverview> findAllWithCustomers() throws DatabaseException;

    /**
     * Gives the table to a customer: table becomes OCCUPIED and a PENDING order is created.
     * Both changes happen together or not at all.
     *
     * @param customerPhone may be null
     * @return the id of the new PENDING order
     * @throws TableUnavailableException if the table is not AVAILABLE any more
     */
    int assignTable(int tableId, String customerName, String customerPhone)
            throws DatabaseException, TableUnavailableException;

    /**
     * Frees an OCCUPIED table and marks any still-open order on it as CANCELLED, in one transaction.
     * (After payment, the Billing milestone frees the table itself and marks the order PAID.)
     *
     * @throws TableUnavailableException if the table is not OCCUPIED
     */
    void releaseTable(int tableId) throws DatabaseException, TableUnavailableException;
}

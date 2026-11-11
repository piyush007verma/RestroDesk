package com.restaurant.dao;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MenuItem;

import java.util.List;

/**
 * Everything the program may ask the database about menu items.
 * This is an interface: the service only knows these method names, not the SQL behind them.
 * InvalidInputException is thrown when the database refuses a change for a reason the user
 * can understand (duplicate name, item already used in orders, item no longer exists).
 */
public interface MenuDao {

    /** All menu items, sorted by category then name. */
    List<MenuItem> findAll() throws DatabaseException;

    /** Saves a new item and returns the id the database gave it. */
    int insert(MenuItem item) throws DatabaseException, InvalidInputException;

    /** Changes name, category, price and availability of the item with the same id. */
    void update(MenuItem item) throws DatabaseException, InvalidInputException;

    void delete(int itemId) throws DatabaseException, InvalidInputException;
}

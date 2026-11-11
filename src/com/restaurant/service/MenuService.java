package com.restaurant.service;

import com.restaurant.dao.MenuDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.MenuItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Business rules for the menu: what counts as a valid name, category and price.
 * The screen passes raw text from the text boxes; this class checks it and only then
 * calls the DAO. No Swing code and no SQL here.
 */
public class MenuService {

    private static final int MAX_NAME_LENGTH = 100;     // matches menu_items.name VARCHAR(100)
    private static final int MAX_CATEGORY_LENGTH = 50;  // matches menu_items.category VARCHAR(50)
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99"); // DECIMAL(10,2)

    /** Suggested categories so a brand-new menu still has choices in the drop-down. */
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("Starters", "Main Course", "Breads", "Desserts", "Beverages");

    private final MenuDao dao;

    public MenuService(MenuDao dao) {
        this.dao = dao;
    }

    public List<MenuItem> getAllItems() throws DatabaseException {
        return dao.findAll();
    }

    public MenuItem addItem(String name, String category, String priceText, boolean available)
            throws InvalidInputException, DatabaseException {
        MenuItem item = validate(0, name, category, priceText, available);
        int newId = dao.insert(item);
        return new MenuItem(newId, item.getName(), item.getCategory(), item.getPrice(), item.isAvailable());
    }

    public MenuItem updateItem(int id, String name, String category, String priceText, boolean available)
            throws InvalidInputException, DatabaseException {
        if (id <= 0) {
            throw new InvalidInputException("Please select an item from the list first.");
        }
        MenuItem item = validate(id, name, category, priceText, available);
        dao.update(item);
        return item;
    }

    public void deleteItem(int id) throws InvalidInputException, DatabaseException {
        if (id <= 0) {
            throw new InvalidInputException("Please select an item from the list first.");
        }
        dao.delete(id);
    }

    /**
     * Categories for the drop-down: the defaults plus every category already used on the menu,
     * without duplicates and sorted A-Z. Uses the Stream API and Collectors.
     */
    public List<String> categoriesFor(List<MenuItem> items) {
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(DEFAULT_CATEGORIES);
        sorted.addAll(items.stream()
                .map(MenuItem::getCategory)
                .collect(Collectors.toList()));
        return List.copyOf(sorted);
    }

    /**
     * The categories that really have dishes (for the category filter), A-Z without duplicates.
     * Unlike {@link #categoriesFor}, the unused default categories are NOT included.
     */
    public List<String> usedCategories(List<MenuItem> items) {
        TreeSet<String> sorted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        sorted.addAll(items.stream()
                .map(MenuItem::getCategory)
                .collect(Collectors.toList()));
        return List.copyOf(sorted);
    }

    // ---------- validation ----------

    private MenuItem validate(int id, String name, String category, String priceText, boolean available)
            throws InvalidInputException {
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isEmpty()) {
            throw new InvalidInputException("Please enter the food item name.");
        }
        if (cleanName.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("The name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }

        String cleanCategory = category == null ? "" : category.trim();
        if (cleanCategory.isEmpty()) {
            throw new InvalidInputException("Please choose or type a category, for example Starters.");
        }
        if (cleanCategory.length() > MAX_CATEGORY_LENGTH) {
            throw new InvalidInputException("The category is too long (maximum " + MAX_CATEGORY_LENGTH
                    + " characters).");
        }

        return new MenuItem(id, cleanName, cleanCategory, parsePrice(priceText), available);
    }

    private BigDecimal parsePrice(String priceText) throws InvalidInputException {
        String text = priceText == null ? "" : priceText.trim();
        if (text.isEmpty()) {
            throw new InvalidInputException("Please enter the price.");
        }
        BigDecimal price;
        try {
            price = new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("The price must be a number, for example 250 or 249.50.");
        }
        if (price.signum() <= 0) {
            throw new InvalidInputException("The price must be greater than 0.");
        }
        if (price.stripTrailingZeros().scale() > 2) {
            throw new InvalidInputException("The price can have at most 2 digits after the decimal point.");
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new InvalidInputException("The price is too large.");
        }
        return price.setScale(2);
    }
}

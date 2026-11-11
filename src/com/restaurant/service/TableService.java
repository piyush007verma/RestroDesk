package com.restaurant.service;

import com.restaurant.dao.TableDao;
import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.exception.TableUnavailableException;
import com.restaurant.model.TableOverview;
import com.restaurant.model.TableStatus;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Business rules for tables: checks the customer's details before a table is assigned.
 * No Swing code and no SQL here. The "table must still be free" rule is enforced by the
 * database inside the DAO's transaction, because only the database can be sure at the exact moment.
 */
public class TableService {

    private static final int MAX_NAME_LENGTH = 100; // orders.customer_name VARCHAR(100)

    /**
     * Letters of any language, spaces, dot, apostrophe and hyphen; must start with a letter.
     * \p{M} allows the vowel signs used in Hindi and other scripts (they are not counted as letters).
     */
    private static final Pattern NAME_PATTERN = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'\\-]*$");
    /** Optional leading +, then 7 to 14 digits (fits orders.customer_phone VARCHAR(15)). */
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?[0-9]{7,14}$");

    private final TableDao dao;

    public TableService(TableDao dao) {
        this.dao = dao;
    }

    public List<TableOverview> getTables() throws DatabaseException {
        return dao.findAllWithCustomers();
    }

    /** How many tables are AVAILABLE and OCCUPIED. Uses Stream API with groupingBy and counting. */
    public Map<TableStatus, Long> countByStatus(List<TableOverview> tables) {
        Map<TableStatus, Long> counts = tables.stream()
                .collect(Collectors.groupingBy(TableOverview::getStatus,
                        () -> new EnumMap<>(TableStatus.class), Collectors.counting()));
        for (TableStatus s : TableStatus.values()) {
            counts.putIfAbsent(s, 0L); // show 0 instead of "missing"
        }
        return counts;
    }

    /**
     * Validates the customer and assigns the table.
     *
     * @return the id of the new PENDING order (Order Management will add food to it)
     */
    public int assignTable(int tableId, String customerName, String customerPhone)
            throws InvalidInputException, TableUnavailableException, DatabaseException {
        if (tableId <= 0) {
            throw new InvalidInputException("Please click a table first.");
        }
        String name = cleanName(customerName);
        String phone = cleanPhone(customerPhone);
        return dao.assignTable(tableId, name, phone);
    }

    public void releaseTable(int tableId)
            throws InvalidInputException, TableUnavailableException, DatabaseException {
        if (tableId <= 0) {
            throw new InvalidInputException("Please click a table first.");
        }
        dao.releaseTable(tableId);
    }

    // ---------- validation ----------

    private String cleanName(String raw) throws InvalidInputException {
        String name = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new InvalidInputException("Please enter the customer's name.");
        }
        if (name.length() < 2) {
            throw new InvalidInputException("The customer's name must have at least 2 letters.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new InvalidInputException("The name is too long (maximum " + MAX_NAME_LENGTH + " characters).");
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            throw new InvalidInputException("The name can contain only letters, spaces and . ' - "
                    + "(no numbers or other symbols).");
        }
        return name;
    }

    /** Phone is optional: returns null when empty. Spaces and hyphens typed by the user are ignored. */
    private String cleanPhone(String raw) throws InvalidInputException {
        String phone = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (phone.isEmpty()) {
            return null;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            throw new InvalidInputException("The phone number must contain 7 to 14 digits "
                    + "(a leading + is allowed), or leave it empty.");
        }
        return phone;
    }
}

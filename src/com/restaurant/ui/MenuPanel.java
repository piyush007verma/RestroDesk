package com.restaurant.ui;

import com.restaurant.model.MenuItem;
import com.restaurant.service.MenuService;
import com.restaurant.ui.components.DataTableStyle;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.SearchField;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Menu Management screen. OWNER: Member 1.
 * Left: a table with every food item. Right: a form to add a new item, or to edit / delete
 * the item selected in the table. All database work runs through Background (SwingWorker).
 * This class only draws things and passes clicks to {@link MenuService}; it has no SQL and no rules.
 */
public class MenuPanel extends ScreenPanel {

    private static final NumberFormat CURRENCY = NumberFormat.getCurrencyInstance(Locale.of("en", "IN"));

    private final MenuService service;

    /** The items currently shown, in the same order as the table rows. */
    private final List<MenuItem> items = new ArrayList<>();
    /** Everything loaded from MySQL; {@code items} is this list after the category filter (Phase 3). */
    private final List<MenuItem> allItems = new ArrayList<>();
    private static final String ALL_CATEGORIES = "All categories";
    private final JComboBox<String> filterBox = new JComboBox<>();
    /** Id of the item being edited, or 0 when the form is in "add new item" mode. */
    private int selectedId = 0;
    private boolean busy = false;
    /** true while the program itself (not the user) is changing the table selection. */
    private boolean filling = false;

    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Name", "Category", "Price", "Status"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel noticeLabel = new JLabel();
    private final JLabel countLabel = new JLabel();

    private final JLabel formTitle = new JLabel("Add New Item");
    private final JTextField nameField = new JTextField();
    private final JComboBox<String> categoryBox = new JComboBox<>();
    private final JTextField priceField = new JTextField();
    private final JCheckBox availableBox = new JCheckBox("Available for ordering", true);

    private final RoundedButton addButton = new RoundedButton("Add Item", Theme.ACCENT);
    private final RoundedButton updateButton = new RoundedButton("Update", Theme.INFO);
    private final RoundedButton deleteButton = new RoundedButton("Delete", Theme.DANGER);
    private final RoundedButton clearButton = new RoundedButton("Clear", Theme.TEXT_MUTED);

    public MenuPanel(MenuService service) {
        super("Menu Management", "Add, edit and remove the dishes you serve");
        this.service = service;

        body.setLayout(new BorderLayout(18, 0));
        body.add(buildTableCard(), BorderLayout.CENTER);
        body.add(buildFormCard(), BorderLayout.EAST);

        addButton.addActionListener(e -> onAdd());
        updateButton.addActionListener(e -> onUpdate());
        deleteButton.addActionListener(e -> onDelete());
        clearButton.addActionListener(e -> enterAddMode());
        priceField.addActionListener(e -> (selectedId == 0 ? addButton : updateButton).doClick());

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || filling) {
                return;
            }
            int row = DataTableStyle.selectedModelRow(table);
            if (row >= 0 && row < items.size()) {
                enterEditMode(items.get(row));
            }
        });

        enterAddMode();
    }

    /** Called every time the user opens this screen: show the latest menu. */
    @Override
    public void onShow() {
        reload(selectedId);
    }

    // ---------- building the screen ----------

    private RoundedPanel buildTableCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        JLabel title = new JLabel("Menu Items");
        title.setFont(Theme.font(java.awt.Font.BOLD, 16));
        title.setForeground(Theme.TEXT_DARK);
        countLabel.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        countLabel.setForeground(Theme.TEXT_MUTED);

        noticeLabel.setFont(Theme.font(java.awt.Font.BOLD, 12));
        noticeLabel.setForeground(Theme.WARNING);
        noticeLabel.setVisible(false);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        filterBox.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        filterBox.setBackground(Color.WHITE);
        filterBox.setToolTipText("Show only one category");
        filterBox.addItem(ALL_CATEGORIES);
        filterBox.addActionListener(e -> {
            if (!filling) {
                render(new ArrayList<>(allItems), selectedId);
            }
        });
        JPanel rightOfTitle = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 12, 0));
        rightOfTitle.setOpaque(false);
        rightOfTitle.add(countLabel);
        rightOfTitle.add(new SearchField("Search dishes...", 14, text -> DataTableStyle.filterRows(table, text)));
        rightOfTitle.add(filterBox);
        titleRow.add(rightOfTitle, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(noticeLabel, BorderLayout.SOUTH);

        card.add(header, BorderLayout.NORTH);
        card.add(DataTableStyle.apply(table, 2), BorderLayout.CENTER);
        DataTableStyle.enableSorting(table);
        DataTableStyle.emptyState(table, "No dishes yet. Add your first dish using the form on the right.", "No dish matches your search.");

        table.getColumnModel().getColumn(3).setCellRenderer(new DataTableStyle.BadgeRenderer(
                text -> text.equals("Available") ? Theme.SUCCESS : Theme.DANGER));
        int[] widths = {230, 150, 110, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return card;
    }

    private RoundedPanel buildFormCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        card.setPreferredSize(new Dimension(340, 0));

        formTitle.setFont(Theme.font(java.awt.Font.BOLD, 16));
        formTitle.setForeground(Theme.TEXT_DARK);
        formTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(formTitle);
        card.add(Box.createVerticalStrut(16));

        styleField(nameField);
        styleField(priceField);
        categoryBox.setEditable(true);
        categoryBox.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        categoryBox.setBackground(Color.WHITE);
        categoryBox.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        if (categoryBox.getEditor().getEditorComponent() instanceof JTextField editor) {
            editor.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6)); // same inner spacing as the other fields
            editor.setForeground(Theme.TEXT_DARK);
        }
        availableBox.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        availableBox.setForeground(Theme.TEXT_DARK);
        availableBox.setOpaque(false);
        availableBox.setFocusPainted(false);

        card.add(fieldLabel("Food name"));
        card.add(formRow(nameField));
        card.add(Box.createVerticalStrut(12));
        card.add(fieldLabel("Category (choose or type a new one)"));
        card.add(formRow(categoryBox));
        card.add(Box.createVerticalStrut(12));
        card.add(fieldLabel("Price (₹)"));
        card.add(formRow(priceField));
        card.add(Box.createVerticalStrut(12));
        availableBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(availableBox);
        card.add(Box.createVerticalStrut(20));

        JPanel buttons = new JPanel(new GridLayout(2, 2, 10, 10));
        buttons.setOpaque(false);
        buttons.add(addButton);
        buttons.add(clearButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        card.add(buttons);
        card.add(Box.createVerticalGlue());
        return card;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        l.setForeground(Theme.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    /** Makes a component fill the card width at a fixed, comfortable height. */
    private static JComponent formRow(JComponent field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setPreferredSize(new Dimension(100, 38));
        return field;
    }

    private static void styleField(JTextField f) {
        f.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        f.setForeground(Theme.TEXT_DARK);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
    }

    // ---------- form modes ----------

    /** Empty form: the next click on "Add Item" creates a new dish. */
    private void enterAddMode() {
        selectedId = 0;
        filling = true;
        table.clearSelection();
        filling = false;
        nameField.setText("");
        categoryBox.setSelectedItem("");
        priceField.setText("");
        availableBox.setSelected(true);
        formTitle.setText("Add New Item");
        applyButtonState();
        nameField.requestFocusInWindow();
    }

    /** Form shows the chosen dish: "Update" and "Delete" work on it. */
    private void enterEditMode(MenuItem item) {
        selectedId = item.getId();
        nameField.setText(item.getName());
        categoryBox.setSelectedItem(item.getCategory());
        priceField.setText(item.getPrice().toPlainString());
        availableBox.setSelected(item.isAvailable());
        formTitle.setText("Edit Item");
        applyButtonState();
    }

    private void applyButtonState() {
        addButton.setEnabled(!busy && selectedId == 0);
        updateButton.setEnabled(!busy && selectedId > 0);
        deleteButton.setEnabled(!busy && selectedId > 0);
        clearButton.setEnabled(!busy);
    }

    private void setBusy(boolean value) {
        busy = value;
        applyButtonState();
    }

    // ---------- button actions ----------

    private void onAdd() {
        String name = nameField.getText();
        String category = categoryText();
        String price = priceField.getText();
        boolean available = availableBox.isSelected();
        runAction(() -> service.addItem(name, category, price, available), saved -> {
            enterAddMode();
            reload(0);
        });
    }

    private void onUpdate() {
        int id = selectedId;
        String name = nameField.getText();
        String category = categoryText();
        String price = priceField.getText();
        boolean available = availableBox.isSelected();
        runAction(() -> service.updateItem(id, name, category, price, available), saved -> reload(id));
    }

    private void onDelete() {
        int id = selectedId;
        String name = nameField.getText().trim();
        if (!Messages.confirm(this, "Delete menu item", "Delete \"" + name + "\" from the menu?")) {
            return;
        }
        runAction(() -> {
            service.deleteItem(id);
            return id;
        }, deletedId -> {
            enterAddMode();
            reload(0);
        });
    }

    private String categoryText() {
        Object value = categoryBox.getEditor().getItem();
        return value == null ? "" : value.toString();
    }

    /** Runs a database action in the background, disabling the buttons meanwhile. */
    private <T> void runAction(Callable<T> task, Consumer<T> onSuccess) {
        setBusy(true);
        Background.run(task, result -> {
            setBusy(false);
            onSuccess.accept(result);
        }, error -> {
            setBusy(false);
            Messages.error(this, error);
        });
    }

    // ---------- loading data ----------

    private void reload(int reselectId) {
        Background.run(service::getAllItems,
                loaded -> render(loaded, reselectId),
                error -> {
                    items.clear();
                    allItems.clear();
                    model.setRowCount(0);
                    countLabel.setText("0 items");
                    noticeLabel.setText(Messages.notice(error));
                    noticeLabel.setToolTipText(error.getMessage());
                    noticeLabel.setVisible(true);
                });
    }

    private void render(List<MenuItem> loaded, int reselectId) {
        noticeLabel.setVisible(false);
        allItems.clear();
        allItems.addAll(loaded);

        // refresh the filter choices (keeping the current choice if that category still exists)
        filling = true;
        Object chosen = filterBox.getSelectedItem();
        filterBox.removeAllItems();
        filterBox.addItem(ALL_CATEGORIES);
        service.usedCategories(allItems).forEach(filterBox::addItem);
        filterBox.setSelectedItem(chosen == null ? ALL_CATEGORIES : chosen);
        if (filterBox.getSelectedItem() == null) {
            filterBox.setSelectedItem(ALL_CATEGORIES);
        }
        Object filter = filterBox.getSelectedItem();
        items.clear();
        for (MenuItem item : allItems) {
            if (ALL_CATEGORIES.equals(filter) || item.getCategory().equalsIgnoreCase(String.valueOf(filter))) {
                items.add(item);
            }
        }

        model.setRowCount(0);
        int rowToSelect = -1;
        for (int i = 0; i < items.size(); i++) {
            MenuItem item = items.get(i);
            model.addRow(new Object[]{
                    item.getName(),
                    item.getCategory(),
                    CURRENCY.format(item.getPrice()),
                    item.isAvailable() ? "Available" : "Unavailable"});
            if (item.getId() == reselectId) {
                rowToSelect = i;
            }
        }
        if (rowToSelect >= 0) {
            DataTableStyle.selectModelRow(table, rowToSelect);
        }
        filling = false;
        countLabel.setText(items.size() == 1 ? "1 item" : items.size() + " items");

        // keep whatever the user typed in the category box, but refresh the choices
        Object typed = categoryBox.getEditor().getItem();
        categoryBox.removeAllItems();
        service.categoriesFor(items).forEach(categoryBox::addItem);
        categoryBox.setSelectedItem(typed == null ? "" : typed);

        if (rowToSelect >= 0) {
            enterEditMode(items.get(rowToSelect));
        } else if (selectedId > 0) {
            enterAddMode(); // the edited item disappeared (for example deleted on another computer)
        }
    }
}

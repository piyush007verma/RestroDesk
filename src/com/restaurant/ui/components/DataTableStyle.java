package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.util.regex.Pattern;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Set;
import java.util.function.Function;

/**
 * Gives a JTable the same clean RestoDesk look (flat header, thin lines, amber selection)
 * so every screen's data table looks the same. Also offers a coloured "pill" cell renderer.
 */
public final class DataTableStyle {

    private DataTableStyle() {
    }

    /**
     * Styles the table and returns it wrapped in a scroll pane that matches the card.
     *
     * @param rightAlignedColumns column numbers (starting at 0) whose text is right aligned, e.g. prices
     */
    public static JScrollPane apply(JTable table, int... rightAlignedColumns) {
        Set<Integer> right = new java.util.HashSet<>();
        for (int c : rightAlignedColumns) {
            right.add(c);
        }

        table.setRowHeight(42);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(Theme.BORDER);
        table.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        table.setForeground(Theme.TEXT_DARK);
        table.setSelectionBackground(new Color(0xFEF3C7));
        table.setSelectionForeground(Theme.TEXT_DARK);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setDefaultRenderer(Object.class, new CellRenderer(right));

        JTableHeader header = table.getTableHeader();
        header.setPreferredSize(new Dimension(0, 36));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer(right));

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.CARD);
        return scroll;
    }

    // ------------------------------------------------------------------ sorting, searching, empty state

    /**
     * Lets the user sort by clicking a column header. Numbers inside text ("Rs 1,100.00", "#12", "T3") sort as numbers.
     * IMPORTANT: once a table can be sorted, a row on screen is not the same as a row in the list.
     * Use {@link #selectedModelRow(JTable)} and {@link #selectModelRow(JTable, int)} instead of getSelectedRow() / setRowSelectionInterval().
     */
    public static void enableSorting(JTable table) {
        TableRowSorter<TableModel> sorter = new TableRowSorter<>(table.getModel());
        for (int c = 0; c < table.getColumnCount(); c++) {
            sorter.setComparator(c, DataTableStyle::compareSmart);
        }
        table.setRowSorter(sorter);
    }

    /** Compares two cell values: as numbers when both contain a number, otherwise as text ignoring case. */
    static int compareSmart(Object a, Object b) {
        String x = String.valueOf(a).trim();
        String y = String.valueOf(b).trim();
        Double nx = number(x);
        Double ny = number(y);
        if (nx != null && ny != null) {
            return Double.compare(nx, ny);
        }
        if (nx != null) {
            return 1;   // "-" or text before numbers
        }
        if (ny != null) {
            return -1;
        }
        return x.compareToIgnoreCase(y);
    }

    private static Double number(String text) {
        String cleaned = text.replace(",", "").replaceAll("^[^0-9-]+", "").replace("*", "");
        if (cleaned.isEmpty() || !cleaned.matches("-?[0-9]+(\\.[0-9]+)?")) {
            return null;
        }
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The row number in the data list (model) of the selected row, or -1. Works with sorting and filtering. */
    public static int selectedModelRow(JTable table) {
        int view = table.getSelectedRow();
        return view < 0 ? -1 : table.convertRowIndexToModel(view);
    }

    /** Selects the row that holds model row {@code modelRow}; clears the selection when it is filtered out. */
    public static void selectModelRow(JTable table, int modelRow) {
        int view = modelRow < 0 ? -1 : table.convertRowIndexToView(modelRow);
        if (view >= 0) {
            table.setRowSelectionInterval(view, view);
            table.scrollRectToVisible(table.getCellRect(view, 0, true));
        } else {
            table.clearSelection();
        }
    }

    /** Shows only the rows that contain {@code text} (any column, ignoring case). Empty text shows everything. */
    @SuppressWarnings("unchecked")
    public static void filterRows(JTable table, String text) {
        if (!(table.getRowSorter() instanceof TableRowSorter<?> sorter)) {
            return;
        }
        String t = text == null ? "" : text.trim();
        ((TableRowSorter<TableModel>) sorter).setRowFilter(t.isEmpty() ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(t)));
    }

    /**
     * Shows a friendly message in the middle of the table while it has no rows to show (nothing to list, or nothing matches
     * the search). {@code whenEmpty} is the text for an empty list, {@code whenFiltered} for "rows exist but none match".
     */
    public static void emptyState(JTable table, String whenEmpty, String whenFiltered) {
        JLabel label = new JLabel("", SwingConstants.CENTER);
        label.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        label.setForeground(Theme.TEXT_MUTED);
        table.setLayout(null);
        table.add(label);
        Runnable update = () -> {
            boolean none = table.getRowCount() == 0;
            label.setVisible(none);
            if (none) {
                label.setText(table.getModel().getRowCount() > 0 ? whenFiltered : whenEmpty);
                int h = 40;
                label.setBounds(0, Math.max(40, table.getParent() == null ? 80 : table.getParent().getHeight() / 2 - h), Math.max(table.getWidth(), 200), h);
            }
        };
        table.getModel().addTableModelListener(e -> SwingUtilities.invokeLater(update));
        table.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                update.run();
            }
        });
        if (table.getRowSorter() != null) { // searching (filtering) changes the visible rows without changing the model
            table.getRowSorter().addRowSorterListener(e -> SwingUtilities.invokeLater(update));
        }
        table.addPropertyChangeListener("rowSorter", e -> update.run());
        SwingUtilities.invokeLater(update);
    }

    /** Plain text cells with comfortable padding. */
    private static class CellRenderer extends DefaultTableCellRenderer {
        private final Set<Integer> rightColumns;

        CellRenderer(Set<Integer> rightColumns) {
            this.rightColumns = rightColumns;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            boolean isRight = rightColumns.contains(column);
            setHorizontalAlignment(isRight ? SwingConstants.RIGHT : SwingConstants.LEFT);
            // The default renderer resets the border for every cell, so we set it after it runs.
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, isRight ? 20 : 10));
            return this;
        }
    }

    private static class HeaderRenderer extends DefaultTableCellRenderer {
        private final Set<Integer> rightColumns;

        HeaderRenderer(Set<Integer> rightColumns) {
            this.rightColumns = rightColumns;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);
            boolean isRight = rightColumns.contains(column);
            setFont(Theme.font(java.awt.Font.BOLD, 12));
            setForeground(Theme.TEXT_MUTED);
            setBackground(Theme.CARD);
            setHorizontalAlignment(isRight ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER),
                    BorderFactory.createEmptyBorder(0, 10, 0, isRight ? 20 : 10)));
            return this;
        }
    }

    /**
     * Draws a text value as a coloured pill. The colour comes from a function you provide,
     * for example {@code new BadgeRenderer(text -> text.equals("Available") ? Theme.SUCCESS : Theme.DANGER)}.
     */
    public static class BadgeRenderer extends DefaultTableCellRenderer {
        private final StatusBadge badge = new StatusBadge();
        /** The badge sits in this panel so the selected-row colour also fills this cell. */
        private final javax.swing.JPanel holder = new javax.swing.JPanel(new java.awt.GridBagLayout());
        private final Function<String, Color> colorFor;

        public BadgeRenderer(Function<String, Color> colorFor) {
            this.colorFor = colorFor;
            holder.add(badge);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            String text = String.valueOf(value);
            badge.setStatus(text, colorFor.apply(text));
            holder.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return holder;
        }
    }
}

package com.restaurant.ui;

import com.restaurant.exception.InvalidInputException;
import com.restaurant.model.TableOverview;
import com.restaurant.model.TableStatus;
import com.restaurant.service.TableService;
import com.restaurant.ui.components.RoundedButton;
import com.restaurant.ui.components.RoundedPanel;
import com.restaurant.ui.components.StatusBadge;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/**
 * Table Management screen. OWNER: Member 1.
 * Left: a card for every table (green = available, red = occupied). Click a card to select it.
 * Right: the selected table's details, with "Assign Table" (free tables) or "Release Table" (occupied).
 * No SQL and no rules here: clicks go to {@link TableService}, work runs through Background.
 */
public class TablesPanel extends ScreenPanel {

    private final TableService service;

    private final List<TableOverview> tables = new ArrayList<>();
    private int selectedTableId = 0;
    private boolean busy = false;
    /** Remembers which table/status the form was last filled for, so typing is not wiped on refresh. */
    private int shownTableId = 0;
    private TableStatus shownStatus = null;

    private final JPanel grid = new JPanel(new GridLayout(0, 3, 14, 14));
    private final JLabel noticeLabel = new JLabel();
    private final StatusBadge availableBadge = new StatusBadge();
    private final StatusBadge occupiedBadge = new StatusBadge();

    private final JLabel detailTitle = new JLabel("No table selected");
    private final JLabel detailInfo = new JLabel("Click a table on the left");
    private final StatusBadge detailBadge = new StatusBadge();
    private final JTextField nameField = new JTextField();
    private final JTextField phoneField = new JTextField();
    private final JLabel orderInfo = new JLabel(" ");
    private final RoundedButton assignButton = new RoundedButton("Assign Table", Theme.ACCENT);
    private final RoundedButton releaseButton = new RoundedButton("Release Table", Theme.DANGER);

    public TablesPanel(TableService service) {
        super("Table Management", "See which tables are free, seat customers and release tables");
        this.service = service;

        body.setLayout(new BorderLayout(18, 0));
        body.add(buildGridCard(), BorderLayout.CENTER);
        body.add(buildDetailCard(), BorderLayout.EAST);

        assignButton.addActionListener(e -> onAssign());
        releaseButton.addActionListener(e -> onRelease());
        phoneField.addActionListener(e -> assignButton.doClick());
        nameField.addActionListener(e -> phoneField.requestFocusInWindow());

        showSelection(null);
    }

    /** Called every time the user opens this screen: show the latest table status. */
    @Override
    public void onShow() {
        reload();
    }

    // ---------- building the screen ----------

    private RoundedPanel buildGridCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));

        JLabel title = new JLabel("All Tables");
        title.setFont(Theme.font(java.awt.Font.BOLD, 16));
        title.setForeground(Theme.TEXT_DARK);

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        legend.setOpaque(false);
        legend.add(availableBadge);
        legend.add(occupiedBadge);
        availableBadge.setStatus("Available 0", Theme.SUCCESS);
        occupiedBadge.setStatus("Occupied 0", Theme.DANGER);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(legend, BorderLayout.EAST);

        noticeLabel.setFont(Theme.font(java.awt.Font.BOLD, 12));
        noticeLabel.setForeground(Theme.WARNING);
        noticeLabel.setVisible(false);

        JPanel header = new JPanel(new BorderLayout(0, 6));
        header.setOpaque(false);
        header.add(titleRow, BorderLayout.NORTH);
        header.add(noticeLabel, BorderLayout.SOUTH);

        grid.setOpaque(false);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(grid, BorderLayout.NORTH); // NORTH keeps cards at their natural height
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        card.add(header, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private RoundedPanel buildDetailCard() {
        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        card.setPreferredSize(new Dimension(340, 0));

        detailTitle.setFont(Theme.font(java.awt.Font.BOLD, 20));
        detailTitle.setForeground(Theme.TEXT_DARK);
        detailInfo.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        detailInfo.setForeground(Theme.TEXT_MUTED);
        orderInfo.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        orderInfo.setForeground(Theme.TEXT_MUTED);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        badgeRow.setOpaque(false);
        badgeRow.add(detailBadge);
        badgeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26)); // do not stretch vertically

        styleField(nameField);
        styleField(phoneField);

        card.add(left(detailTitle));
        card.add(Box.createVerticalStrut(4));
        card.add(left(detailInfo));
        card.add(Box.createVerticalStrut(10));
        card.add(left(badgeRow));
        card.add(Box.createVerticalStrut(22));
        card.add(left(fieldLabel("Customer name")));
        card.add(formRow(nameField));
        card.add(Box.createVerticalStrut(12));
        card.add(left(fieldLabel("Phone (optional)")));
        card.add(formRow(phoneField));
        card.add(Box.createVerticalStrut(8));
        card.add(left(orderInfo));
        card.add(Box.createVerticalStrut(18));

        JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 10));
        buttons.setOpaque(false);
        buttons.add(assignButton);
        buttons.add(releaseButton);
        buttons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 86));
        card.add(left(buttons));
        card.add(Box.createVerticalGlue());
        return card;
    }

    private static <T extends JComponent> T left(T c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.font(java.awt.Font.PLAIN, 12));
        l.setForeground(Theme.TEXT_MUTED);
        l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        return l;
    }

    private static JComponent formRow(JComponent field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        field.setPreferredSize(new Dimension(100, 38));
        return field;
    }

    private static void styleField(JTextField f) {
        f.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        f.setForeground(Theme.TEXT_DARK);
        f.setDisabledTextColor(Theme.TEXT_DARK);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
    }

    // ---------- showing data ----------

    private void reload() {
        Background.run(service::getTables, this::render, error -> {
            tables.clear();
            grid.removeAll();
            grid.revalidate();
            grid.repaint();
            availableBadge.setStatus("Available 0", Theme.SUCCESS);
            occupiedBadge.setStatus("Occupied 0", Theme.DANGER);
            noticeLabel.setText(Messages.notice(error));
            noticeLabel.setToolTipText(error.getMessage());
            noticeLabel.setVisible(true);
            showSelection(null);
        });
    }

    private void render(List<TableOverview> loaded) {
        noticeLabel.setVisible(false);
        tables.clear();
        tables.addAll(loaded);

        Map<TableStatus, Long> counts = service.countByStatus(tables);
        availableBadge.setStatus("Available " + counts.get(TableStatus.AVAILABLE), Theme.SUCCESS);
        occupiedBadge.setStatus("Occupied " + counts.get(TableStatus.OCCUPIED), Theme.DANGER);

        TableOverview selected = null;
        grid.removeAll();
        for (TableOverview t : tables) {
            boolean isSelected = t.getId() == selectedTableId;
            if (isSelected) {
                selected = t;
            }
            grid.add(new TableCard(t, isSelected, () -> select(t)));
        }
        grid.revalidate();
        grid.repaint();

        showSelection(selected); // null if the selected table no longer exists
    }

    private void select(TableOverview t) {
        selectedTableId = t.getId();
        // redraw the cards so the selection highlight moves, then fill the side panel
        grid.removeAll();
        for (TableOverview other : tables) {
            grid.add(new TableCard(other, other.getId() == selectedTableId, () -> select(other)));
        }
        grid.revalidate();
        grid.repaint();
        showSelection(t);
    }

    /** Fills the right-hand panel for the chosen table (or clears it when t is null). */
    private void showSelection(TableOverview t) {
        if (t == null) {
            selectedTableId = 0;
            shownTableId = 0;
            shownStatus = null;
            detailTitle.setText("No table selected");
            detailInfo.setText("Click a table on the left");
            detailBadge.setStatus("-", Theme.TEXT_MUTED);
            nameField.setText("");
            phoneField.setText("");
            orderInfo.setText(" ");
            nameField.setEnabled(false);
            phoneField.setEnabled(false);
            applyButtonState();
            return;
        }

        boolean free = t.getStatus() == TableStatus.AVAILABLE;
        detailTitle.setText("Table " + t.getNumber());
        detailInfo.setText(t.getCapacity() + " seats");
        detailBadge.setStatus(free ? "Available" : "Occupied", free ? Theme.SUCCESS : Theme.DANGER);

        boolean changed = t.getId() != shownTableId || t.getStatus() != shownStatus;
        if (changed) {
            // a different table, or the status changed: refill the fields
            nameField.setText(free ? "" : nullToEmpty(t.getCustomerName()));
            phoneField.setText(free ? "" : nullToEmpty(t.getCustomerPhone()));
        }
        shownTableId = t.getId();
        shownStatus = t.getStatus();

        nameField.setEnabled(free);
        phoneField.setEnabled(free);
        orderInfo.setText(free ? "Enter the customer details to seat them."
                : (t.getOpenOrderId() > 0 ? "Open order #" + t.getOpenOrderId() : "No open order on this table."));
        applyButtonState();
        if (free && changed) {
            nameField.requestFocusInWindow();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private TableOverview selectedTable() {
        return tables.stream().filter(t -> t.getId() == selectedTableId).findFirst().orElse(null);
    }

    private void applyButtonState() {
        TableOverview t = selectedTable();
        assignButton.setEnabled(!busy && t != null && t.getStatus() == TableStatus.AVAILABLE);
        releaseButton.setEnabled(!busy && t != null && t.getStatus() == TableStatus.OCCUPIED);
    }

    // ---------- button actions ----------

    private void onAssign() {
        int id = selectedTableId;
        String name = nameField.getText();
        String phone = phoneField.getText();
        runAction(() -> service.assignTable(id, name, phone), orderId -> {
            shownStatus = null; // force the panel to refill for the new OCCUPIED state
            reload();
        });
    }

    private void onRelease() {
        TableOverview t = selectedTable();
        if (t == null) {
            return;
        }
        String who = t.getCustomerName() == null ? "" : "\nCustomer: " + t.getCustomerName();
        boolean yes = Messages.confirm(this, "Release table",
                "Release Table " + t.getNumber() + "?" + who
                        + "\n\nAny open order on this table will be marked CANCELLED."
                        + "\nUse this when guests leave without paying.");
        if (!yes) {
            return;
        }
        int id = t.getId();
        runAction(() -> {
            service.releaseTable(id);
            return id;
        }, releasedId -> {
            shownStatus = null;
            reload();
        });
    }

    /** Runs a database action in the background. A failed action also refreshes the list. */
    private <T> void runAction(Callable<T> task, Consumer<T> onSuccess) {
        busy = true;
        applyButtonState();
        Background.run(task, result -> {
            busy = false;
            onSuccess.accept(result);
        }, error -> {
            busy = false;
            applyButtonState(); // buttons work again straight away
            Messages.error(this, error);
            if (!(error instanceof InvalidInputException)) {
                reload(); // the table state may have changed under us: show the truth
            }
        });
    }

    // ---------- one table card ----------

    /** A clickable rounded card for one table. Green = available, red = occupied. */
    private static class TableCard extends RoundedPanel {

        TableCard(TableOverview t, boolean selected, Runnable onClick) {
            boolean free = t.getStatus() == TableStatus.AVAILABLE;
            Color accent = free ? Theme.SUCCESS : Theme.DANGER;
            setBackground(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), selected ? 40 : 18));
            setBorderColor(selected ? Theme.ACCENT : new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 90));
            setLayout(new BorderLayout(0, 8));
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            setPreferredSize(new Dimension(150, 118));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel number = new JLabel("Table " + t.getNumber());
            number.setFont(Theme.font(java.awt.Font.BOLD, 18));
            number.setForeground(Theme.TEXT_DARK);
            JLabel seats = new JLabel(t.getCapacity() + " seats");
            seats.setFont(Theme.font(java.awt.Font.PLAIN, 12));
            seats.setForeground(Theme.TEXT_MUTED);

            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);
            top.add(number, BorderLayout.WEST);
            top.add(seats, BorderLayout.EAST);

            StatusBadge badge = new StatusBadge();
            badge.setStatus(free ? "Available" : "Occupied", accent);
            JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            badgeRow.setOpaque(false);
            badgeRow.add(badge);

            String who = free ? "Ready for guests"
                    : (t.getCustomerName() == null ? "In use" : t.getCustomerName());
            JLabel customer = new JLabel(who);
            customer.setFont(Theme.font(java.awt.Font.PLAIN, 12));
            customer.setForeground(Theme.TEXT_MUTED);

            add(top, BorderLayout.NORTH);
            add(badgeRow, BorderLayout.CENTER);
            add(customer, BorderLayout.SOUTH);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.run();
                }
            });
        }
    }
}

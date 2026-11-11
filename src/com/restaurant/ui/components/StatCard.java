package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A dashboard "number card": a title, a big value and a coloured marker. */
public class StatCard extends RoundedPanel {

    private final JLabel valueLabel = new JLabel("-");
    private final Color accent;

    public StatCard(String title, Color accent) {
        super(18, Theme.CARD);
        this.accent = accent;
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));
        setPreferredSize(new Dimension(220, 104));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(java.awt.Font.PLAIN, 13));
        titleLabel.setForeground(Theme.TEXT_MUTED);

        valueLabel.setFont(Theme.font(java.awt.Font.BOLD, 28));
        valueLabel.setForeground(Theme.TEXT_DARK);

        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.setOpaque(false);
        text.add(titleLabel, BorderLayout.NORTH);
        text.add(valueLabel, BorderLayout.CENTER);
        add(text, BorderLayout.CENTER);
    }

    private boolean hovering;

    /** Makes the whole card clickable (hover highlight, hand cursor, tooltip, Enter / Space when focused). */
    public void setOnClick(Runnable action, String tooltip) {
        setToolTipText(tooltip);
        setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        setFocusable(true);
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hovering = false;
                repaint();
            }

            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                action.run();
            }
        });
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("ENTER"), "open");
        getInputMap(WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("SPACE"), "open");
        getActionMap().put("open", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                action.run();
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
    }

    public void setValue(String value) {
        // long amounts (for example Rs 1,25,000.00) get a smaller font so they are never cut off
        valueLabel.setFont(Theme.font(java.awt.Font.BOLD, value.length() > 11 ? 20 : value.length() > 8 ? 24 : 28));
        valueLabel.setText(value);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // small coloured dot in the top-right corner
        if (hovering || isFocusOwner()) { // clickable card: coloured outline on hover / keyboard focus
            g2.setColor(accent);
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
        }
        g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
        g2.fillOval(getWidth() - 48, 18, 28, 28);
        g2.setColor(accent);
        g2.fillOval(getWidth() - 40, 26, 12, 12);
        g2.dispose();
    }
}

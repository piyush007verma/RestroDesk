package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** One navigation item in the dark sidebar. Shows a letter badge, a label and an active highlight. */
public class SidebarButton extends JButton {

    private boolean active;
    private boolean hovering;
    /** True when focus arrived by keyboard (Tab); mouse clicks do not draw the focus ring. */
    private boolean keyFocus;
    private final String badgeLetter;

    public SidebarButton(String text, String badgeLetter) {
        super(text);
        this.badgeLetter = badgeLetter;
        setHorizontalAlignment(LEFT);
        setFont(Theme.font(java.awt.Font.PLAIN, 14));
        setForeground(Theme.SIDEBAR_TEXT);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 58, 0, 12));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(220, 46));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                keyFocus = java.awt.EventQueue.getCurrentEvent() instanceof java.awt.event.KeyEvent;
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                keyFocus = false;
                repaint();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovering = false;
                repaint();
            }
        });
    }

    public void setActive(boolean active) {
        this.active = active;
        setForeground(active ? Color.WHITE : Theme.SIDEBAR_TEXT);
        setFont(Theme.font(active ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 14));
        repaint();
    }

    /** Draws the icon for a screen around the centre point (cx, cy); the letter chooses the icon. */
    private static void drawIcon(Graphics2D g2, String key, int cx, int cy) {
        switch (key) {
            case "D" -> { // dashboard: four tiles
                g2.drawRect(cx - 7, cy - 7, 6, 6);
                g2.drawRect(cx + 1, cy - 7, 6, 6);
                g2.drawRect(cx - 7, cy + 1, 6, 6);
                g2.drawRect(cx + 1, cy + 1, 6, 6);
            }
            case "M" -> { // menu: list with bullets
                for (int i = -1; i <= 1; i++) {
                    g2.fillOval(cx - 8, cy + i * 6 - 1, 3, 3);
                    g2.drawLine(cx - 3, cy + i * 6, cx + 8, cy + i * 6);
                }
            }
            case "T" -> { // table: round top with a leg
                g2.drawOval(cx - 8, cy - 7, 16, 8);
                g2.drawLine(cx, cy + 1, cx, cy + 7);
                g2.drawLine(cx - 5, cy + 7, cx + 5, cy + 7);
            }
            case "O" -> { // orders: clipboard with lines
                g2.drawRoundRect(cx - 6, cy - 7, 12, 15, 3, 3);
                g2.drawLine(cx - 3, cy - 2, cx + 3, cy - 2);
                g2.drawLine(cx - 3, cy + 2, cx + 3, cy + 2);
                g2.fillRect(cx - 2, cy - 9, 5, 3);
            }
            case "B" -> { // billing: receipt with zig-zag bottom
                int[] xs = {cx - 6, cx - 6, cx + 6, cx + 6, cx + 3, cx, cx - 3};
                int[] ys = {cy + 8, cy - 8, cy - 8, cy + 8, cy + 5, cy + 8, cy + 5};
                g2.drawPolyline(xs, ys, 7);
                g2.drawLine(cx - 3, cy - 3, cx + 3, cy - 3);
                g2.drawLine(cx - 3, cy + 1, cx + 3, cy + 1);
            }
            case "H" -> { // history: clock
                g2.drawOval(cx - 8, cy - 8, 16, 16);
                g2.drawLine(cx, cy, cx, cy - 5);
                g2.drawLine(cx, cy, cx + 4, cy + 2);
            }
            case "R" -> { // reports: bars
                g2.drawLine(cx - 7, cy + 8, cx + 8, cy + 8);
                g2.fillRect(cx - 6, cy, 3, 8);
                g2.fillRect(cx - 1, cy - 6, 3, 14);
                g2.fillRect(cx + 4, cy - 3, 3, 11);
            }
            default -> g2.drawString(key, cx - 4, cy + 4);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (active || hovering) {
            g2.setColor(active ? Theme.NAVY_ACTIVE : Theme.NAVY_HOVER);
            g2.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 14, 14);
        }
        if (active) {
            g2.setColor(Theme.ACCENT);
            g2.fillRoundRect(0, 12, 4, getHeight() - 24, 4, 4);
        }

        // icon badge: a small vector icon drawn with Java2D (no image files needed)
        int size = 30;
        int bx = 16;
        int by = (getHeight() - size) / 2;
        g2.setColor(active ? Theme.ACCENT : (hovering ? new Color(0x35496D) : new Color(0x2A3C5A)));
        g2.fillRoundRect(bx, by, size, size, 10, 10);
        g2.setColor(active ? Theme.NAVY : Color.WHITE);
        g2.setStroke(new java.awt.BasicStroke(1.8f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        drawIcon(g2, badgeLetter, bx + size / 2, by + size / 2);
        if (isFocusOwner() && keyFocus) { // keyboard focus ring
            g2.setColor(Theme.ACCENT);
            g2.setStroke(new java.awt.BasicStroke(1.5f));
            g2.drawRoundRect(1, 3, getWidth() - 3, getHeight() - 7, 14, 14);
        }
        g2.dispose();

        super.paintComponent(g);
    }
}

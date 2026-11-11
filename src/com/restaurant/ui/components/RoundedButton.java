package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

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

/** A modern flat button with rounded corners and a hover effect. Ready for the next milestones. */
public class RoundedButton extends JButton {

    private final Color normal;
    private final Color hover;
    private boolean hovering;
    /** True when focus arrived by keyboard (Tab); mouse clicks do not draw the focus ring. */
    private boolean keyFocus;

    public RoundedButton(String text, Color normal) {
        super(text);
        this.normal = normal;
        this.hover = normal.darker();
        setForeground(Color.WHITE);
        setFont(Theme.font(java.awt.Font.BOLD, 13));
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setFocusable(true);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 36, 38);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (!isEnabled()) {
            // Disabled look: soft grey button with clearly readable white text.
            g2.setColor(new Color(0xCBD5E1));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(Color.WHITE);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(getText(), x, y);
            g2.dispose();
            return;
        }
        boolean pressed = getModel().isPressed();
        g2.setColor(pressed ? normal.darker().darker() : hovering ? hover : normal);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
        if (isFocusOwner() && keyFocus) { // keyboard focus ring (Tab to a button, press Space or Enter)
            g2.setColor(new Color(255, 255, 255, 200));
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.drawRoundRect(3, 3, getWidth() - 7, getHeight() - 7, 10, 10);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

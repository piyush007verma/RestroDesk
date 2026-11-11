package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A panel with rounded corners, a soft border and a background colour. Used for all "cards". */
public class RoundedPanel extends JPanel {

    private final int arc;
    private Color borderColor = Theme.BORDER;

    public RoundedPanel(int arc, Color background) {
        this.arc = arc;
        setOpaque(false);
        setBackground(background);
    }

    public RoundedPanel() {
        this(18, Theme.CARD);
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(getBackground());
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        if (borderColor != null) {
            g2.setColor(borderColor);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}

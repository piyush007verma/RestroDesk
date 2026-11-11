package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A small coloured "pill" showing a status such as PAID or OCCUPIED. */
public class StatusBadge extends JLabel {

    private Color color = Theme.TEXT_MUTED;

    public StatusBadge() {
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(Theme.font(java.awt.Font.BOLD, 11));
        setOpaque(false);
    }

    public void setStatus(String text, Color color) {
        this.color = color;
        setText(text);
        setForeground(color);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont());
        return new Dimension(fm.stringWidth(getText()) + 22, 24);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        FontMetrics fm = g2.getFontMetrics(getFont());
        int w = Math.min(getWidth(), fm.stringWidth(getText()) + 22);
        int h = Math.min(getHeight(), 24);
        int x = (getWidth() - w) / 2;
        int y = (getHeight() - h) / 2;
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
        g2.fillRoundRect(x, y, w, h, h, h);
        g2.dispose();
        super.paintComponent(g);
    }
}

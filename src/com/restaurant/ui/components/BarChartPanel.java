package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A very small bar chart drawn with plain Java 2D (no chart library). Give it one label and one number per bar.
 * Hover a bar to see its exact value.
 */
public class BarChartPanel extends JPanel {

    private final Color barColor;
    private final Function<BigDecimal, String> format;
    private final List<String> labels = new ArrayList<>();
    private final List<BigDecimal> values = new ArrayList<>();
    private String emptyText = "No data for this period";

    public BarChartPanel(Color barColor, Function<BigDecimal, String> format) {
        this.barColor = barColor;
        this.format = format;
        setOpaque(false);
        setPreferredSize(new Dimension(300, 150));
        setToolTipText("");
    }

    public void setData(List<String> newLabels, List<BigDecimal> newValues) {
        labels.clear();
        values.clear();
        labels.addAll(newLabels);
        values.addAll(newValues);
        repaint();
    }

    /** Number of bars currently drawn (used by tests). */
    public int barCount() {
        return values.size();
    }

    private int barSlot() {
        return values.isEmpty() ? 1 : Math.max(1, (getWidth() - 16) / values.size());
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (values.isEmpty()) {
            return null;
        }
        int index = (e.getX() - 8) / barSlot();
        return index >= 0 && index < values.size() ? labels.get(index) + ": " + format.apply(values.get(index)) : null;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(Theme.font(java.awt.Font.PLAIN, 11));
        FontMetrics fm = g2.getFontMetrics();
        int w = getWidth();
        int h = getHeight();

        if (values.isEmpty()) {
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(emptyText, (w - fm.stringWidth(emptyText)) / 2, h / 2);
            g2.dispose();
            return;
        }

        int top = fm.getHeight() + 4;               // room for the value above the tallest bar
        int bottom = h - fm.getHeight() - 6;        // baseline; labels go below
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ONE);
        if (max.signum() <= 0) {
            max = BigDecimal.ONE;
        }
        int slot = barSlot();
        int barWidth = Math.min(46, Math.max(4, slot - 8));

        g2.setColor(Theme.BORDER);
        g2.drawLine(8, bottom, w - 8, bottom);

        for (int i = 0; i < values.size(); i++) {
            int x = 8 + i * slot + (slot - barWidth) / 2;
            int barHeight = values.get(i).multiply(BigDecimal.valueOf(bottom - top))
                    .divide(max, 0, java.math.RoundingMode.HALF_UP).intValue();
            if (values.get(i).signum() > 0) {
                barHeight = Math.max(barHeight, 2);
            }
            g2.setColor(barColor);
            g2.fillRoundRect(x, bottom - barHeight, barWidth, barHeight, 6, 6);

            String value = format.apply(values.get(i));
            if (slot >= fm.stringWidth(value) + 4) {
                g2.setColor(Theme.TEXT_DARK);
                g2.drawString(value, x + (barWidth - fm.stringWidth(value)) / 2, bottom - barHeight - 3);
            }
            String label = fit(labels.get(i), fm, slot - 2);
            g2.setColor(Theme.TEXT_MUTED);
            g2.drawString(label, 8 + i * slot + (slot - fm.stringWidth(label)) / 2, bottom + fm.getHeight());
        }
        g2.dispose();
    }

    /** Shortens text with ".." until it fits into the given width. */
    private static String fit(String text, FontMetrics fm, int width) {
        if (fm.stringWidth(text) <= width) {
            return text;
        }
        String t = text;
        while (t.length() > 1 && fm.stringWidth(t + "..") > width) {
            t = t.substring(0, t.length() - 1);
        }
        return fm.stringWidth(t + "..") > width ? "" : t + "..";
    }
}

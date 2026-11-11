package com.restaurant.ui;

import com.restaurant.ui.components.RoundedPanel;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import java.awt.Component;

/**
 * Temporary screen shown for features that are not built yet. It lists what the
 * screen will contain and which team member owns it. Each real screen later
 * replaces its own placeholder subclass, so members never edit the same file.
 */
public class PlaceholderScreen extends ScreenPanel {

    public PlaceholderScreen(String title, String subtitle, String owner, String... plannedFeatures) {
        super(title, subtitle);

        RoundedPanel card = new RoundedPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        card.add(label("Coming in a later milestone", Theme.font(java.awt.Font.BOLD, 18), Theme.TEXT_DARK));
        card.add(spacer(6));
        card.add(label("Owner: " + owner, Theme.font(java.awt.Font.PLAIN, 14), Theme.ACCENT.darker()));
        card.add(spacer(18));
        card.add(label("Planned features", Theme.font(java.awt.Font.BOLD, 14), Theme.TEXT_DARK));
        card.add(spacer(8));
        for (String feature : plannedFeatures) {
            card.add(label("•  " + feature, Theme.font(java.awt.Font.PLAIN, 14), Theme.TEXT_MUTED));
            card.add(spacer(5));
        }

        body.add(card, BorderLayout.NORTH);
    }

    private static JLabel label(String text, java.awt.Font font, java.awt.Color color) {
        JLabel l = new JLabel(text);
        l.setFont(font);
        l.setForeground(color);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static Component spacer(int height) {
        return javax.swing.Box.createVerticalStrut(height);
    }
}

package com.restaurant.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/**
 * Base class for every screen (Dashboard, Menu, Tables ...).
 * It draws the page title and gives each screen an empty "body" area to fill.
 * MainFrame calls onShow() each time the user opens the screen, so a screen
 * can refresh its data from the database at that moment.
 */
public abstract class ScreenPanel extends JPanel {

    /** Put your screen's content in here. */
    protected final JPanel body = new JPanel(new BorderLayout());

    protected ScreenPanel(String title, String subtitle) {
        setLayout(new BorderLayout(0, 22));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(Theme.font(java.awt.Font.BOLD, 26));
        titleLabel.setForeground(Theme.TEXT_DARK);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(Theme.font(java.awt.Font.PLAIN, 14));
        subtitleLabel.setForeground(Theme.TEXT_MUTED);

        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.CENTER);

        body.setOpaque(false);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
    }

    /** Called every time this screen becomes visible. Override to refresh data. */
    public void onShow() {
        // default: nothing to refresh
    }
}

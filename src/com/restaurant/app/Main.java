package com.restaurant.app;

import com.restaurant.ui.MainFrame;

import javax.swing.SwingUtilities;

/** Program entry point. Swing windows must be created on the Event Dispatch Thread. */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            // consistent fonts for dialogs, tooltips and drop-downs (the screens themselves use Theme)
            java.awt.Font body = com.restaurant.ui.Theme.font(java.awt.Font.PLAIN, 13);
            for (String key : new String[]{"OptionPane.messageFont", "OptionPane.buttonFont", "ToolTip.font", "ComboBox.font"}) {
                javax.swing.UIManager.put(key, body);
            }
            javax.swing.UIManager.put("ToolTip.background", new java.awt.Color(0x0F172A));
            javax.swing.UIManager.put("ToolTip.foreground", java.awt.Color.WHITE);
            javax.swing.UIManager.put("ToolTip.border", javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            new MainFrame().setVisible(true);
        });
    }
}

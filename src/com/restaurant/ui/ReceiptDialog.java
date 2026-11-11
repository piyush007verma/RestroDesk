package com.restaurant.ui;

import com.restaurant.ui.components.RoundedButton;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.print.PrinterException;

/** A pop-up window with the receipt text and a Print button (Java's built-in printing, no extra library). */
public final class ReceiptDialog {

    private ReceiptDialog() {
    }

    public static void show(Component parent, String title, String receiptText) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), title, JDialog.ModalityType.APPLICATION_MODAL);
        JTextArea area = new JTextArea(receiptText);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setBackground(Color.WHITE);
        area.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        area.setName("receiptText");

        RoundedButton print = new RoundedButton("Print", Theme.ACCENT);
        RoundedButton close = new RoundedButton("Close", Theme.TEXT_MUTED);
        print.addActionListener(e -> {
            try {
                area.print(); // opens the system print dialog (choose "Microsoft Print to PDF" to save a PDF)
            } catch (PrinterException ex) {
                Messages.error(dialog, new Exception("The receipt could not be printed: " + ex.getMessage()));
            }
        });
        close.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        buttons.add(print);
        buttons.add(close);
        dialog.setLayout(new BorderLayout());
        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(520, 640);
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }
}

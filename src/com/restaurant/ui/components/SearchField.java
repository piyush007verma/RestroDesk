package com.restaurant.ui.components;

import com.restaurant.ui.Theme;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/** A search box with grey hint text ("Search...") that reports every change of its text, so lists filter as you type. */
public class SearchField extends JTextField {

    private final String hint;

    public SearchField(String hint, int columns, Consumer<String> onChange) {
        super(columns);
        this.hint = hint;
        setFont(Theme.font(java.awt.Font.PLAIN, 13));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        setToolTipText("Type to filter the list (Esc clears)");
        getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                onChange.accept(getText());
            }
        });
        getInputMap().put(javax.swing.KeyStroke.getKeyStroke("ESCAPE"), "clearSearch");
        getActionMap().put("clearSearch", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                setText("");
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (getText().isEmpty()) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(0x94A3B8));
            g2.setFont(getFont());
            g2.drawString(hint, getInsets().left, (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2);
            g2.dispose();
        }
    }
}

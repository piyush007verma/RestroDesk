package com.restaurant.ui;

import com.restaurant.exception.DatabaseException;
import com.restaurant.exception.InvalidInputException;
import com.restaurant.exception.TableUnavailableException;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Friendly pop-up dialogs. Users never see Java stack traces, only a clear sentence. */
public final class Messages {

    private Messages() {
    }

    /** Shows a suitable dialog for any exception thrown by a service. */
    public static void error(Component parent, Exception e) {
        if (e instanceof InvalidInputException) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Please check your input",
                    JOptionPane.WARNING_MESSAGE);
        } else if (e instanceof TableUnavailableException) {
            JOptionPane.showMessageDialog(parent, e.getMessage(), "Table not available",
                    JOptionPane.WARNING_MESSAGE);
        } else if (e instanceof DatabaseException) {
            JOptionPane.showMessageDialog(parent,
                    "The database could not be reached or returned an error.\n"
                            + "Check that MySQL is running and config/db.properties is correct.\n\n"
                            + "Details: " + e.getMessage(),
                    "Database problem", JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(parent, "Something went wrong: " + e.getMessage(),
                    "Unexpected problem", JOptionPane.ERROR_MESSAGE);
        }
    }

    public static void info(Component parent, String title, String text) {
        JOptionPane.showMessageDialog(parent, text, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Asks a Yes/No question. Returns true only when the user clicks Yes. */
    public static boolean confirm(Component parent, String title, String text) {
        return JOptionPane.showConfirmDialog(parent, text, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    /**
     * Text for the orange notice shown on a screen when data could not be loaded.
     * It shows the REAL reason (what is wrong and how to fix it), wrapped over several lines.
     * The reason is also printed to the console so it can be copied.
     */
    public static String notice(Exception e) {
        String reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        System.err.println("[RestoDesk] " + reason);
        String prefix = e instanceof DatabaseException ? "Database not connected. " : "Could not load data. ";
        return wrap(prefix + reason, 440);
    }

    /** Turns plain text into a label text that wraps at the given width in pixels (and is HTML-safe). */
    public static String wrap(String text, int widthPixels) {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
        return "<html><body style='width: " + widthPixels + "px'>" + safe + "</body></html>";
    }
}

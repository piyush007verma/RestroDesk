package com.restaurant.ui;

import java.awt.Color;
import java.awt.Font;

/**
 * One place for every colour and font in the application.
 * Rule for the team: never type a colour or font directly in a screen -
 * always use Theme, so the whole app stays visually consistent.
 */
public final class Theme {

    private Theme() {
    }

    // Sidebar
    public static final Color NAVY = new Color(0x0F1B2D);
    public static final Color NAVY_HOVER = new Color(0x1A2B45);
    public static final Color NAVY_ACTIVE = new Color(0x22375A);
    public static final Color SIDEBAR_TEXT = new Color(0xA9B6CB);

    // Main area
    public static final Color BACKGROUND = new Color(0xF4F6FA);
    public static final Color CARD = Color.WHITE;
    public static final Color BORDER = new Color(0xE3E8F0);
    public static final Color TEXT_DARK = new Color(0x1E293B);
    public static final Color TEXT_MUTED = new Color(0x64748B);

    // Brand and status colours
    public static final Color ACCENT = new Color(0xF59E0B);
    public static final Color SUCCESS = new Color(0x16A34A);
    public static final Color DANGER = new Color(0xDC2626);
    public static final Color INFO = new Color(0x2563EB);
    public static final Color WARNING = new Color(0xD97706);

    public static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    /** Colour that matches an order status such as PENDING or PAID. */
    public static Color statusColor(String status) {
        if (status == null) {
            return TEXT_MUTED;
        }
        switch (status.toUpperCase()) {
            case "PENDING":
                return WARNING;
            case "PREPARING":
                return INFO;
            case "READY":
                return new Color(0x0891B2);
            case "SERVED":
                return new Color(0x7C3AED);
            case "PAID":
            case "COMPLETED":
                return SUCCESS;
            case "CANCELLED":
                return DANGER;
            default:
                return TEXT_MUTED;
        }
    }
}

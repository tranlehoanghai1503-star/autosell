package com.hai.selldonutsmp.util;

import java.util.Locale;

public final class Money {
    private Money() {}

    /** Doc so tien dang "1,234,567", "$1.5M", "250k"... Nem NumberFormatException neu sai. */
    public static double parse(String raw) {
        String s = raw.trim().replace(",", "").replace("$", "").replace(" ", "");
        while (s.endsWith(".")) s = s.substring(0, s.length() - 1);

        double mult = 1;
        if (!s.isEmpty()) {
            char c = Character.toLowerCase(s.charAt(s.length() - 1));
            switch (c) {
                case 'k' -> mult = 1e3;
                case 'm' -> mult = 1e6;
                case 'b' -> mult = 1e9;
                case 't' -> mult = 1e12;
                default -> {}
            }
            if (mult != 1) s = s.substring(0, s.length() - 1);
        }
        return Double.parseDouble(s) * mult;
    }

    public static String format(double v) {
        double a = Math.abs(v);
        String s;
        if (a >= 1e12) s = String.format(Locale.US, "%.2fT", v / 1e12);
        else if (a >= 1e9) s = String.format(Locale.US, "%.2fB", v / 1e9);
        else if (a >= 1e6) s = String.format(Locale.US, "%.2fM", v / 1e6);
        else if (a >= 1e3) s = String.format(Locale.US, "%.2fK", v / 1e3);
        else s = String.format(Locale.US, "%.0f", v);
        return "$" + s;
    }
}

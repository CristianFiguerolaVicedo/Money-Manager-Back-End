package com.cristian.moneymanager.util;

/** Escapes LIKE wildcards so a search for "50%" matches the text "50%", not "50" followed by anything. */
public final class LikeEscape {

    private LikeEscape() {
    }

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}

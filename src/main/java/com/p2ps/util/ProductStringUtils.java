package com.p2ps.util;

public class ProductStringUtils {

    private ProductStringUtils() {
        // Utility class
    }

    /**
     * Returns the first non-null, non-blank string from the provided values.
     * The result is trimmed.
     *
     * @param values The strings to check.
     * @return The first trimmed non-blank string, or null if none found.
     */
    public static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

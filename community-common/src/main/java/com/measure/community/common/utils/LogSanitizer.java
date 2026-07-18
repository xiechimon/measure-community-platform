package com.measure.community.common.utils;

import java.util.Set;

public final class LogSanitizer {

    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "password", "token", "secret", "idcard", "phone");

    private LogSanitizer() {
    }

    /**
     * Sanitizes a raw query string for logging. Only the key before {@code =} receives
     * safe ASCII {@code %HH} normalization for sensitive-key matching; values and the
     * complete query are never URL decoded.
     */
    public static String sanitizeQuery(String query) {
        if (query == null || query.isEmpty()) {
            return "EMPTY";
        }

        String[] segments = query.split("&", -1);
        StringBuilder sanitized = new StringBuilder(query.length());
        for (int index = 0; index < segments.length; index++) {
            if (index > 0) {
                sanitized.append('&');
            }

            String segment = segments[index];
            int separator = segment.indexOf('=');
            if (separator >= 0 && isSensitiveKey(segment.substring(0, separator))) {
                sanitized.append(segment, 0, separator + 1).append("***");
            } else {
                sanitized.append(segment);
            }
        }
        return sanitized.toString();
    }

    private static boolean isSensitiveKey(String key) {
        return SENSITIVE_QUERY_KEYS.contains(normalizeAsciiPercentEscapes(key).toLowerCase(java.util.Locale.ROOT));
    }

    private static String normalizeAsciiPercentEscapes(String key) {
        StringBuilder normalized = new StringBuilder(key.length());
        for (int index = 0; index < key.length(); index++) {
            char current = key.charAt(index);
            if (current == '%' && index + 2 < key.length()) {
                int high = Character.digit(key.charAt(index + 1), 16);
                int low = Character.digit(key.charAt(index + 2), 16);
                int decoded = high >= 0 && low >= 0 ? (high << 4) + low : -1;
                if (decoded >= 0 && decoded <= 0x7F) {
                    normalized.append((char) decoded);
                    index += 2;
                    continue;
                }
            }
            normalized.append(current);
        }
        return normalized.toString();
    }
}

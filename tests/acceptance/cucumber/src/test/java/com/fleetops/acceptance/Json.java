package com.fleetops.acceptance;

import java.util.Map;

/**
 * Flat JSON objects for request bodies. Written by hand so the bodies do not
 * depend on which JSON mapper REST Assured finds, and so a test can send a
 * deliberately wrong type, such as a string where a number belongs.
 */
final class Json {

    private Json() {
    }

    static String object(Map<String, ?> fields) {
        StringBuilder body = new StringBuilder("{");
        for (Map.Entry<String, ?> field : fields.entrySet()) {
            if (body.length() > 1) {
                body.append(',');
            }
            body.append(quote(field.getKey())).append(':');
            Object value = field.getValue();
            body.append(value instanceof Number || value instanceof Boolean ? value.toString() : quote(String.valueOf(value)));
        }
        return body.append('}').toString();
    }

    private static String quote(String value) {
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }
}

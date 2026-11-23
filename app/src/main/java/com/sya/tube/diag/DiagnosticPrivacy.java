package com.sya.tube.diag;

import java.util.regex.Pattern;

/** Remove addresses, query credentials and explicitly labelled secrets before storage or sharing. */
public final class DiagnosticPrivacy {
    private static final Pattern AUTHORIZATION = Pattern.compile("(?im)(authorization\\s*:\\s*)[^\\r\\n|]+");
    private static final Pattern QUOTED_SECRET = Pattern.compile("(?i)((?:[a-z_-]*token|password|api[_-]?key)\\s*[=:]\\s*)[\\\"]([^\\\"]*)[\\\"]");
    private static final Pattern ADDRESS = Pattern.compile("(?i)(?:https?|content|file)://[^\\s<>\"]+");
    private static final Pattern SECRET = Pattern.compile("(?i)((?:[a-z_-]*token|password|authorization|api[_-]?key)\\s*[=:]\\s*)[^\\s,;|]+");

    public static String clean(String text) {
        if (text == null) return "";
        String clean = AUTHORIZATION.matcher(text).replaceAll("$1[redacted]");
        clean = QUOTED_SECRET.matcher(clean).replaceAll("$1[redacted]");
        clean = ADDRESS.matcher(clean).replaceAll("[url]");
        return SECRET.matcher(clean).replaceAll("$1[redacted]");
    }
    private DiagnosticPrivacy() {}
}

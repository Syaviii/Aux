package com.sya.tube.diag;

/** Remove addresses, query credentials and explicitly labelled secrets before storage or sharing. */
public final class DiagnosticPrivacy {
    public static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("(?im)(authorization\\s*:\\s*)[^\\r\\n|]+", "$1[redacted]")
            .replaceAll("(?i)((?:[a-z_-]*token|password|api[_-]?key)\\s*[=:]\\s*)[\\\"]([^\\\"]*)[\\\"]", "$1[redacted]")
            .replaceAll("(?i)(?:https?|content|file)://[^\\s<>\"]+", "[url]")
            .replaceAll("(?i)((?:[a-z_-]*token|password|authorization|api[_-]?key)\\s*[=:]\\s*)[^\\s,;|]+", "$1[redacted]");
    }
    private DiagnosticPrivacy() {}
}

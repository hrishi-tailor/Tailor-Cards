package com.tailorcards.api.security;

/** Makes request-supplied values safe to log: line breaks and other control characters can't forge log entries. */
public final class LogSafe {

    private LogSafe() {
    }

    public static String of(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value);
        return s.length() > 200 ? clean(s.substring(0, 200)) + "..." : clean(s);
    }

    private static String clean(String s) {
        return s.replaceAll("\\p{Cntrl}", "_");
    }
}

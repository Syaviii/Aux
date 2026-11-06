package com.sya.tube.update;
import java.math.BigInteger;

final class ReleaseVersion {
    static boolean valid(String value) { return value != null && value.length() <= 100 && value.matches("[0-9]+(?:\\.[0-9]+)*"); }
    static int compare(String a, String b) {
        if (!valid(a) || !valid(b)) return 0;
        String[] x = a.split("\\."), y = b.split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            BigInteger p = i < x.length ? new BigInteger(x[i]) : BigInteger.ZERO;
            BigInteger q = i < y.length ? new BigInteger(y[i]) : BigInteger.ZERO;
            int result = p.compareTo(q);
            if (result != 0) return result;
        }
        return 0;
    }
    private ReleaseVersion() {}
}

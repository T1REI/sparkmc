package sparkmc.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Util {
    private static final Pattern NON_DIGIT = Pattern.compile("[^0-9]+");

    private Util() {}

    public static int cmpVersion(String a, String b) {
        List<Long> pa = parts(a);
        List<Long> pb = parts(b);
        int n = Math.max(pa.size(), pb.size());
        for (int i = 0; i < n; i++) {
            long x = i < pa.size() ? pa.get(i) : 0L;
            long y = i < pb.size() ? pb.get(i) : 0L;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    private static List<Long> parts(String v) {
        List<Long> out = new ArrayList<>();
        if (v == null || v.isBlank()) {
            return out;
        }
        for (String p : NON_DIGIT.split(v)) {
            if (!p.isEmpty()) {
                try {
                    out.add(Long.parseLong(p));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return out;
    }
}

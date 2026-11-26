package industries.angelic.depensurer.api.processor;

/**
 * Checks Maven version range syntax as FML's VersionRange reads it, except the bare {@code 1.0} form, which FML treats
 * as a recommendation that matches every version.
 */
final class VersionRanges {

    private VersionRanges() {}

    /** Returns null when the spec is valid, otherwise what is wrong with it. */
    static String check(String spec) {
        String s = spec.trim();
        if (s.isEmpty()) {
            return "is empty";
        }
        int i = 0;
        while (true) {
            char open = s.charAt(i);
            if (open != '[' && open != '(') {
                return "must be a bracketed range such as [1.0,2.0), [1.6] or [1.0,)";
            }
            int close = indexOfClose(s, i + 1);
            if (close < 0) {
                return "has an unclosed range";
            }
            String problem = checkRestriction(open, s.substring(i + 1, close), s.charAt(close));
            if (problem != null) {
                return problem;
            }
            i = close + 1;
            if (i == s.length()) {
                return null;
            }
            if (s.charAt(i) != ',' || i + 1 == s.length()) {
                return "must separate ranges with a comma";
            }
            i++;
        }
    }

    private static int indexOfClose(String s, int from) {
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ']' || c == ')') {
                return i;
            }
            if (c == '[' || c == '(') {
                return -1;
            }
        }
        return -1;
    }

    private static String checkRestriction(char open, String body, char close) {
        int comma = body.indexOf(',');
        if (comma < 0) {
            if (open != '[' || close != ']') {
                return "must write a single version as [1.0]";
            }
            return body.trim()
                .isEmpty() ? "has an empty range" : null;
        }
        if (body.indexOf(',', comma + 1) >= 0) {
            return "has more than two bounds in one range";
        }
        String lower = body.substring(0, comma)
            .trim();
        String upper = body.substring(comma + 1)
            .trim();
        if (lower.isEmpty() && upper.isEmpty()) {
            return "has a range without bounds";
        }
        if (lower.isEmpty() && open != '(') {
            return "must open an unbounded lower end with (";
        }
        if (upper.isEmpty() && close != ')') {
            return "must close an unbounded upper end with )";
        }
        return null;
    }
}

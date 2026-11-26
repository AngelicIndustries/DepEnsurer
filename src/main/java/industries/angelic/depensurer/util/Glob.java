package industries.angelic.depensurer.util;

import java.util.regex.Pattern;

/** A jar path glob: {@code *} and {@code ?} stay within a path segment, {@code **} crosses segments. */
public final class Glob {

    private static final String REGEX_SPECIAL = "\\.[]{}()<>+-=!^$|";

    public final String glob;
    private final Pattern pattern;
    private final String root;

    public Glob(String glob) {
        this.glob = glob;
        StringBuilder regex = new StringBuilder();
        int firstWildcard = -1;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*' || c == '?') {
                if (firstWildcard < 0) {
                    firstWildcard = i;
                }
                if (c == '?') {
                    regex.append("[^/]");
                } else if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    i++;
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '/') {
                        i++;
                        regex.append("(?:.*/)?");
                    } else {
                        regex.append(".*");
                    }
                } else {
                    regex.append("[^/]*");
                }
            } else {
                if (REGEX_SPECIAL.indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
            }
        }
        this.pattern = Pattern.compile(regex.toString());
        if (firstWildcard < 0) {
            this.root = glob;
        } else {
            this.root = glob.substring(0, glob.lastIndexOf('/', firstWildcard) + 1);
        }
    }

    public boolean matches(String path) {
        return pattern.matcher(path)
            .matches();
    }

    /** The literal start of the glob, cut back to a folder: {@code assets/aether/} for {@code assets/aether/**}. */
    public String root() {
        return root;
    }

    /** True when the root has two or more segments, like {@code assets/aether/}: narrow enough to recognize a jar. */
    public boolean isSpecific() {
        int segments = 0;
        for (String segment : root.split("/")) {
            if (!segment.isEmpty()) {
                segments++;
            }
        }
        return segments >= 2;
    }

    @Override
    public String toString() {
        return glob;
    }
}

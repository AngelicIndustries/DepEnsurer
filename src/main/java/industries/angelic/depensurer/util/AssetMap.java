package industries.angelic.depensurer.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import industries.angelic.depensurer.declaration.DepDeclaration;

/**
 * Which jar entries a dep serves, and under which paths: the entries matching an include and no exclude, moved by the
 * remap rules. A remapped entry is only served under its new path.
 */
public final class AssetMap {

    private final Map<String, String> jarPathByServed = new LinkedHashMap<>();
    private final List<String> collisions = new ArrayList<>();

    public AssetMap(Iterable<String> jarEntries, List<String> include, List<String> exclude,
        List<DepDeclaration.Remap> remap) {
        List<Glob> includes = globs(include);
        List<Glob> excludes = globs(exclude);
        List<DepDeclaration.Remap> rules = new ArrayList<>(remap);
        // Longest prefix first, so the most specific rule wins
        rules.sort((a, b) -> b.from.length() - a.from.length());
        for (String entry : jarEntries) {
            if (entry.endsWith("/") || !matchesAny(includes, entry) || matchesAny(excludes, entry)) {
                continue;
            }
            String served = served(entry, rules);
            if (jarPathByServed.containsKey(served)) {
                collisions.add(entry + " -> " + served);
            } else {
                jarPathByServed.put(served, entry);
            }
        }
    }

    /** The jar entry served at this path, or null. */
    public String jarPath(String servedPath) {
        return jarPathByServed.get(servedPath);
    }

    /** The resource domains served: {@code x} for every path under {@code assets/x/}. */
    public Set<String> domains() {
        Set<String> domains = new TreeSet<>();
        for (String served : jarPathByServed.keySet()) {
            if (served.startsWith("assets/")) {
                int slash = served.indexOf('/', "assets/".length());
                if (slash > "assets/".length()) {
                    domains.add(served.substring("assets/".length(), slash));
                }
            }
        }
        return domains;
    }

    public int size() {
        return jarPathByServed.size();
    }

    /** Entries that remapped onto a path another entry already took; the first one wins. */
    public List<String> collisions() {
        return Collections.unmodifiableList(collisions);
    }

    private static String served(String entry, List<DepDeclaration.Remap> rules) {
        for (DepDeclaration.Remap rule : rules) {
            if (rule.from.endsWith("/") ? entry.startsWith(rule.from) : entry.equals(rule.from)) {
                return rule.to + entry.substring(rule.from.length());
            }
        }
        return entry;
    }

    private static List<Glob> globs(List<String> patterns) {
        List<Glob> globs = new ArrayList<>();
        for (String pattern : patterns) {
            globs.add(new Glob(pattern));
        }
        return globs;
    }

    private static boolean matchesAny(List<Glob> globs, String path) {
        for (Glob glob : globs) {
            if (glob.matches(path)) {
                return true;
            }
        }
        return false;
    }
}

package industries.angelic.depensurer;

/**
 * Which build this is. The CurseForge jar replaces this class with one that returns true; it's a method, not a
 * constant, so callers aren't compiled with the value inlined.
 */
public final class Flavor {

    private Flavor() {}

    public static boolean isCurseForge() {
        return false;
    }
}

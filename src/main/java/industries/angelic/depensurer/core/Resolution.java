package industries.angelic.depensurer.core;

import java.io.File;
import java.util.Collections;
import java.util.List;

import industries.angelic.depensurer.declaration.DepDeclaration;
import industries.angelic.depensurer.declaration.ModDeclaration;

/** What the coremod found and did. Built once at coremod time and read by the mod phase. */
public final class Resolution {

    private static volatile Resolution current;

    public final List<ModDeclaration> declarations;
    /** One per satisfied dep group (a primary dep and its alternatives). */
    public final List<Resolved> resolved;
    /** One per unsatisfied group, optional ones included. */
    public final List<Missing> missing;
    public final List<Problem> problems;
    /** File names Dep Ensurer renamed to .jar.disabled on this launch. */
    public final List<String> disabled;

    Resolution(List<ModDeclaration> declarations, List<Resolved> resolved, List<Missing> missing,
        List<Problem> problems, List<String> disabled) {
        this.declarations = Collections.unmodifiableList(declarations);
        this.resolved = Collections.unmodifiableList(resolved);
        this.missing = Collections.unmodifiableList(missing);
        this.problems = Collections.unmodifiableList(problems);
        this.disabled = Collections.unmodifiableList(disabled);
    }

    /** Null until the coremod ran. */
    public static Resolution current() {
        return current;
    }

    static void setCurrent(Resolution resolution) {
        current = resolution;
    }

    /** For when the coremod never ran, which means the jar's manifest lost its FMLCorePlugin entry. */
    public static Resolution notRun() {
        return failed(new IllegalStateException("Dep Ensurer's coremod didn't run"));
    }

    static Resolution failed(Throwable t) {
        return new Resolution(
            Collections.<ModDeclaration>emptyList(),
            Collections.<Resolved>emptyList(),
            Collections.<Missing>emptyList(),
            Collections.singletonList(
                new Problem(Problem.Kind.SCAN_FAILED, "Dep Ensurer's scan of the mods folder failed: " + t, null)),
            Collections.<String>emptyList());
    }

    public boolean restartNeeded() {
        for (Problem problem : problems) {
            if (problem.kind == Problem.Kind.LATE) {
                return true;
            }
        }
        return false;
    }

    /** The jar serving a dep group of a mod, by the id of the primary dep or of the alternative in use. */
    public Resolved find(String modId, String depId) {
        for (Resolved r : resolved) {
            if (r.owner.modId.equals(modId) && (r.primary.id.equals(depId) || r.chosen.id.equals(depId))) {
                return r;
            }
        }
        return null;
    }

    public static final class Resolved {

        public final ModDeclaration owner;
        public final DepDeclaration primary;
        /** The primary dep or the alternative that matched. */
        public final DepDeclaration chosen;
        public final File file;
        /** Matched by sha1 rather than by mod id and version. */
        public final boolean exact;

        Resolved(ModDeclaration owner, DepDeclaration primary, DepDeclaration chosen, File file, boolean exact) {
            this.owner = owner;
            this.primary = primary;
            this.chosen = chosen;
            this.file = file;
            this.exact = exact;
        }
    }

    public static final class Missing {

        public final ModDeclaration owner;
        public final DepDeclaration primary;
        /** Jars with the dep's mod id that didn't match its version or contents. */
        public final List<File> wrongVersions;

        Missing(ModDeclaration owner, DepDeclaration primary, List<File> wrongVersions) {
            this.owner = owner;
            this.primary = primary;
            this.wrongVersions = Collections.unmodifiableList(wrongVersions);
        }
    }

    public static final class Problem {

        public enum Kind {
            /** A mod's META-INF/depensurer.json can't be read. */
            BROKEN_DECLARATION,
            /** A jar couldn't be renamed. */
            RENAME_FAILED,
            /** FML examined a jar with early hooks before Dep Ensurer could disable it. */
            LATE,
            /** The scan itself threw. */
            SCAN_FAILED
        }

        public final Kind kind;
        public final String message;
        /** Null when no single file is at fault. */
        public final File file;

        Problem(Kind kind, String message, File file) {
            this.kind = kind;
            this.message = message;
            this.file = file;
        }
    }
}

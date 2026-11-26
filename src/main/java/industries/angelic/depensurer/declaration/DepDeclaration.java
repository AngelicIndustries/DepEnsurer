package industries.angelic.depensurer.declaration;

import java.util.Collections;
import java.util.List;

import industries.angelic.depensurer.api.EnsureDep;

/** One {@link EnsureDep}, as the annotation processor wrote it. Strings are trimmed, absent ones are empty. */
public final class DepDeclaration {

    public final String id;
    public final String name;
    public final String file;
    /** Lowercase hex. */
    public final List<String> sha1;
    public final String modId;
    public final String versions;
    public final String alternativeOf;
    /** Null when not on CurseForge. */
    public final CurseForge curseforge;
    /** Null when not on Modrinth. */
    public final Modrinth modrinth;
    public final List<String> urls;
    public final List<String> include;
    public final List<String> exclude;
    public final List<Remap> remap;
    public final boolean neutralize;
    public final EnsureDep.Side side;
    public final boolean optional;

    public DepDeclaration(String id, String name, String file, List<String> sha1, String modId, String versions,
        String alternativeOf, CurseForge curseforge, Modrinth modrinth, List<String> urls, List<String> include,
        List<String> exclude, List<Remap> remap, boolean neutralize, EnsureDep.Side side, boolean optional) {
        this.id = id;
        this.name = name;
        this.file = file;
        this.sha1 = Collections.unmodifiableList(sha1);
        this.modId = modId;
        this.versions = versions;
        this.alternativeOf = alternativeOf;
        this.curseforge = curseforge;
        this.modrinth = modrinth;
        this.urls = Collections.unmodifiableList(urls);
        this.include = Collections.unmodifiableList(include);
        this.exclude = Collections.unmodifiableList(exclude);
        this.remap = Collections.unmodifiableList(remap);
        this.neutralize = neutralize;
        this.side = side;
        this.optional = optional;
    }

    public boolean isAlternative() {
        return !alternativeOf.isEmpty();
    }

    /** The name to show players: the declared name, else the file name, else the id. */
    public String displayName() {
        if (!name.isEmpty()) {
            return name;
        }
        return file.isEmpty() ? id : file;
    }

    public static final class CurseForge {

        public final int project;
        public final String slug;
        public final int file;

        public CurseForge(int project, String slug, int file) {
            this.project = project;
            this.slug = slug;
            this.file = file;
        }
    }

    public static final class Modrinth {

        public final String project;
        public final String version;

        public Modrinth(String project, String version) {
            this.project = project;
            this.version = version;
        }
    }

    public static final class Remap {

        public final String from;
        public final String to;

        public Remap(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }
}

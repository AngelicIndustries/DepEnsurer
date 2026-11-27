package industries.angelic.depensurer;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.core.Resolution;
import industries.angelic.depensurer.declaration.DepDeclaration;

/** What to tell the player: files to get, with links, and problems. Empty when loading can go on. */
public final class Report {

    public final List<Entry> missing;
    public final List<String> problems;
    /** Dep Ensurer disabled a jar Forge had already started loading. */
    public final boolean restart;

    private Report(List<Entry> missing, List<String> problems, boolean restart) {
        this.missing = Collections.unmodifiableList(missing);
        this.problems = Collections.unmodifiableList(problems);
        this.restart = restart;
    }

    /**
     * @param dedicatedServer leave out deps only the client needs
     * @param modNames        mod id to display name
     */
    public static Report of(Resolution resolution, boolean dedicatedServer, Function<String, String> modNames) {
        return of(resolution, dedicatedServer, modNames, Collections.<Resolution.Missing, String>emptyMap());
    }

    /** @param downloadErrors why the full build couldn't download a missing group */
    public static Report of(Resolution resolution, boolean dedicatedServer, Function<String, String> modNames,
        Map<Resolution.Missing, String> downloadErrors) {
        List<Entry> missing = new ArrayList<>();
        for (Resolution.Missing m : resolution.missing) {
            DepDeclaration primary = m.primary;
            if (primary.optional || (dedicatedServer && primary.side == EnsureDep.Side.CLIENT)) {
                continue;
            }
            List<String> wrong = new ArrayList<>();
            for (File file : m.wrongVersions) {
                wrong.add(file.getName());
            }
            List<Link> links = links(primary);
            if (links.isEmpty()) {
                for (DepDeclaration dep : m.owner.deps) {
                    if (primary.id.equals(dep.alternativeOf) && !links(dep).isEmpty()) {
                        links = links(dep);
                        break;
                    }
                }
            }
            missing.add(
                new Entry(
                    primary.displayName(),
                    primary.file,
                    modNames.apply(m.owner.modId),
                    wrong,
                    links,
                    downloadErrors.get(m)));
        }
        List<String> problems = new ArrayList<>();
        for (Resolution.Problem problem : resolution.problems) {
            problems.add(problem.message);
        }
        return new Report(missing, problems, resolution.restartNeeded());
    }

    public boolean isEmpty() {
        return missing.isEmpty() && problems.isEmpty() && !restart;
    }

    /** Download pages on CurseForge and Modrinth. Raw mirror URLs are for the full build's downloader only. */
    static List<Link> links(DepDeclaration dep) {
        List<Link> links = new ArrayList<>();
        if (dep.curseforge != null) {
            links.add(
                new Link(
                    "CurseForge",
                    dep.curseforge.slug.isEmpty() ? "https://www.curseforge.com/projects/" + dep.curseforge.project
                        : "https://www.curseforge.com/minecraft/mc-mods/" + dep.curseforge.slug
                            + "/files/"
                            + dep.curseforge.file));
        }
        if (dep.modrinth != null) {
            links.add(
                new Link(
                    "Modrinth",
                    "https://modrinth.com/mod/" + dep.modrinth.project + "/version/" + dep.modrinth.version));
        }
        return links;
    }

    /** The report as plain lines, for logs and the dedicated server. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        if (!missing.isEmpty()) {
            lines.add("These files are needed. Put them in the mods folder and restart:");
            for (Entry entry : missing) {
                StringBuilder line = new StringBuilder("  ").append(entry.name);
                if (!entry.file.isEmpty() && !entry.file.equals(entry.name)) {
                    line.append(" (")
                        .append(entry.file)
                        .append(')');
                }
                line.append(", needed by ")
                    .append(entry.neededBy);
                if (!entry.wrongVersions.isEmpty()) {
                    line.append(", wrong version installed: ")
                        .append(String.join(", ", entry.wrongVersions));
                }
                if (entry.downloadError != null) {
                    line.append(", download failed: ")
                        .append(entry.downloadError);
                }
                for (Link link : entry.links) {
                    line.append(" | ")
                        .append(link.url);
                }
                lines.add(line.toString());
            }
        }
        lines.addAll(problems);
        return lines;
    }

    public static final class Entry {

        public final String name;
        /** Empty when the dep has no file name. */
        public final String file;
        public final String neededBy;
        public final List<String> wrongVersions;
        public final List<Link> links;
        /** Why the full build couldn't download it; null when it didn't try. */
        public final String downloadError;

        Entry(String name, String file, String neededBy, List<String> wrongVersions, List<Link> links,
            String downloadError) {
            this.name = name;
            this.file = file;
            this.neededBy = neededBy;
            this.wrongVersions = Collections.unmodifiableList(wrongVersions);
            this.links = Collections.unmodifiableList(links);
            this.downloadError = downloadError;
        }
    }

    public static final class Link {

        public final String label;
        public final String url;

        Link(String label, String url) {
            this.label = label;
            this.url = url;
        }
    }
}

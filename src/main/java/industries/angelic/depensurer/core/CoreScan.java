package industries.angelic.depensurer.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.InvalidVersionSpecificationException;
import cpw.mods.fml.common.versioning.VersionRange;
import industries.angelic.depensurer.core.Resolution.Problem;
import industries.angelic.depensurer.declaration.DeclarationException;
import industries.angelic.depensurer.declaration.DeclarationReader;
import industries.angelic.depensurer.declaration.DepDeclaration;
import industries.angelic.depensurer.declaration.ModDeclaration;
import industries.angelic.depensurer.util.Glob;

/**
 * The coremod's work: read the declarations, find the declared jars, disable the ones in the mods folder and decide
 * which jar serves each dep. Keeps no state between launches; everything is decided from the files present.
 */
public final class CoreScan {

    private static final Logger LOG = LogManager.getLogger("DepEnsurerCore");
    private static final String DISABLED = ".disabled";

    private final File modsDir;
    private final File versionedModsDir;
    private final List<File> storeDirs;
    private final File self;
    private final ClassLoader classpath;

    private final List<Problem> problems = new ArrayList<>();
    private final List<String> disabled = new ArrayList<>();
    /** How many jars were hashed, for tests of the prefilter. */
    int hashed;

    /**
     * @param modsDir   the mods folder; its 1.7.10 subfolder is scanned too
     * @param storeDirs folders holding jars outside the mods folder (depensurer/jars)
     * @param self      Dep Ensurer's own jar or class folder, never a candidate; null if unknown
     * @param classpath searched for declarations of mods on the class path (dev runs); may be null
     */
    public CoreScan(File modsDir, List<File> storeDirs, File self, ClassLoader classpath) {
        this.modsDir = canonical(modsDir);
        this.versionedModsDir = new File(this.modsDir, "1.7.10");
        this.storeDirs = storeDirs;
        this.self = self == null ? null : canonical(self);
        this.classpath = classpath;
    }

    public Resolution run() {
        Set<File> declaringJars = new HashSet<>();
        List<ModDeclaration> declarations = collectDeclarations(declaringJars);
        if (declarations.isEmpty()) {
            return result(
                declarations,
                Collections.<Resolution.Resolved>emptyList(),
                Collections.<Resolution.Missing>emptyList());
        }

        List<Dep> deps = new ArrayList<>();
        for (ModDeclaration mod : declarations) {
            for (DepDeclaration dep : mod.deps) {
                deps.add(new Dep(mod, dep));
            }
        }
        List<Candidate> candidates = findCandidates(declaringJars);
        inspect(candidates, deps);
        neutralize(candidates, deps);
        return resolve(declarations, deps, candidates);
    }

    private Resolution result(List<ModDeclaration> declarations, List<Resolution.Resolved> resolved,
        List<Resolution.Missing> missing) {
        return new Resolution(declarations, resolved, missing, problems, disabled);
    }

    // Declarations

    private List<ModDeclaration> collectDeclarations(Set<File> declaringJars) {
        Map<String, ModDeclaration> byClass = new LinkedHashMap<>();
        for (File jar : jarsIn(modsDir, versionedModsDir)) {
            if (jar.equals(self)) {
                continue;
            }
            try (ZipFile zip = new ZipFile(jar)) {
                ZipEntry entry = zip.getEntry(DeclarationReader.PATH);
                if (entry == null) {
                    continue;
                }
                declaringJars.add(jar);
                try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
                    add(byClass, DeclarationReader.read(reader, relative(jar)));
                }
            } catch (DeclarationException e) {
                problems.add(new Problem(Problem.Kind.BROKEN_DECLARATION, e.getMessage(), jar));
            } catch (IOException e) {
                LOG.warn("Can't read {}, skipping it: {}", relative(jar), e.toString());
            }
        }
        if (classpath != null) {
            try {
                Enumeration<URL> urls = classpath.getResources(DeclarationReader.PATH);
                while (urls.hasMoreElements()) {
                    URL url = urls.nextElement();
                    try (InputStream in = url.openStream();
                        Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        add(byClass, DeclarationReader.read(reader, url.toString()));
                    } catch (DeclarationException e) {
                        problems.add(new Problem(Problem.Kind.BROKEN_DECLARATION, e.getMessage(), null));
                    }
                }
            } catch (IOException e) {
                LOG.warn("Can't search the class path for declarations: {}", e.toString());
            }
        }
        if (!byClass.isEmpty()) {
            LOG.info("{} mods declare asset sources", byClass.size());
        }
        return new ArrayList<>(byClass.values());
    }

    private static void add(Map<String, ModDeclaration> byClass, List<ModDeclaration> mods) {
        for (ModDeclaration mod : mods) {
            if (!byClass.containsKey(mod.className)) {
                byClass.put(mod.className, mod);
            }
        }
    }

    // Candidates

    private List<Candidate> findCandidates(Set<File> declaringJars) {
        List<Candidate> candidates = new ArrayList<>();
        Set<File> seen = new HashSet<>();
        for (File dir : Arrays.asList(modsDir, versionedModsDir)) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File file : files) {
                String name = file.getName();
                if (!file.isFile() || file.equals(self) || declaringJars.contains(file)) {
                    continue;
                }
                if (name.endsWith(".jar")) {
                    candidates.add(new Candidate(file, Place.MODS));
                } else if (name.endsWith(".jar" + DISABLED)) {
                    candidates.add(new Candidate(file, Place.MODS_DISABLED));
                }
            }
        }
        for (File dir : storeDirs) {
            for (File jar : jarsIn(dir)) {
                if (seen.add(canonical(jar))) {
                    candidates.add(new Candidate(jar, Place.STORE));
                }
            }
        }
        return candidates;
    }

    /** Reads each candidate's metadata and the declared paths it has, and hashes the ones that could match. */
    private void inspect(List<Candidate> candidates, List<Dep> deps) {
        Set<String> roots = new LinkedHashSet<>();
        Map<String, List<Glob>> globsByModId = new HashMap<>();
        boolean hashEverything = false;
        for (Dep dep : deps) {
            roots.addAll(dep.specificRoots);
            if (!dep.declared.modId.isEmpty()) {
                List<Glob> globs = globsByModId.get(dep.declared.modId);
                if (globs == null) {
                    globs = new ArrayList<>();
                    globsByModId.put(dep.declared.modId, globs);
                }
                globs.addAll(dep.includes);
            }
            hashEverything |= dep.unrecognizable();
        }
        for (Candidate candidate : candidates) {
            try {
                candidate.info = JarInfo.read(candidate.file, roots, globsByModId);
            } catch (IOException e) {
                LOG.warn("Can't read {}, skipping it: {}", relative(candidate.file), e.toString());
                candidate.info = JarInfo.empty();
                continue;
            }
            if (hashEverything || anyMayMatch(candidate, deps)) {
                try {
                    candidate.sha1 = sha1(candidate.file);
                    hashed++;
                } catch (IOException e) {
                    LOG.warn("Can't hash {}: {}", relative(candidate.file), e.toString());
                }
            }
        }
    }

    private static boolean anyMayMatch(Candidate candidate, List<Dep> deps) {
        for (Dep dep : deps) {
            if (dep.declared.sha1.isEmpty()) {
                continue;
            }
            if (dep.declared.file.equals(candidate.baseName)
                || (!dep.declared.modId.isEmpty() && candidate.info.mods.containsKey(dep.declared.modId))) {
                return true;
            }
            for (String root : dep.specificRoots) {
                if (candidate.info.rootHits.contains(root)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Matching

    private static Match match(Dep dep, Candidate candidate) {
        if (candidate.sha1 != null && dep.declared.sha1.contains(candidate.sha1)) {
            return Match.EXACT;
        }
        String modId = dep.declared.modId;
        if (modId.isEmpty() || !candidate.info.mods.containsKey(modId)) {
            return Match.NONE;
        }
        if (dep.range != null
            && !dep.range.containsVersion(new DefaultArtifactVersion(modId, candidate.info.mods.get(modId)))) {
            return Match.WRONG_VERSION;
        }
        if (dep.versionsInvalid) {
            return Match.WRONG_VERSION;
        }
        for (Glob include : dep.includes) {
            if (!candidate.info.globHits.contains(include.glob)) {
                return Match.WRONG_VERSION;
            }
        }
        return Match.LOOSE;
    }

    // Neutralizing

    private void neutralize(List<Candidate> candidates, List<Dep> deps) {
        for (Candidate candidate : candidates) {
            if (candidate.place != Place.MODS) {
                continue;
            }
            Dep reason = null;
            for (Dep dep : deps) {
                if (dep.declared.neutralize && match(dep, candidate) != Match.NONE) {
                    reason = dep;
                    break;
                }
            }
            if (reason == null) {
                continue;
            }
            boolean late = examinedBeforeUs(candidate) && candidate.info.earlyHooks;
            String name = candidate.file.getName();
            String what = relative(candidate.file);
            try {
                File target = new File(candidate.file.getParentFile(), candidate.file.getName() + DISABLED);
                if (target.exists() && sameContent(candidate.file, target)) {
                    // A launcher that re-syncs the mods folder put the jar back: replace the identical copy
                    Files.move(candidate.file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    target = freeDisabledName(candidate.file);
                    Files.move(candidate.file.toPath(), target.toPath());
                }
                disabled.add(candidate.file.getName());
                LOG.info(
                    "Disabled {} -> {}: asset source '{}' of {}.{}",
                    what,
                    target.getName(),
                    reason.declared.id,
                    reason.owner.modId,
                    selfInMods() && !late ? " FML logs \"Unable to read the jar file\" for it once, that's expected."
                        : "");
                candidate.file = target;
                candidate.place = Place.MODS_DISABLED;
            } catch (IOException e) {
                problems.add(
                    new Problem(
                        Problem.Kind.RENAME_FAILED,
                        "Couldn't disable " + what
                            + " ("
                            + e
                            + "). Close programs using it, or rename it to "
                            + candidate.file.getName()
                            + DISABLED
                            + " yourself.",
                        candidate.file));
                continue;
            }
            if (late) {
                problems.add(
                    new Problem(
                        Problem.Kind.LATE,
                        "Forge loaded " + name
                            + " before Dep Ensurer could disable it. Dep Ensurer has disabled it now; restart the game. "
                            + "If this happens every time, make sure Dep Ensurer's jar name starts with +.",
                        candidate.file));
            }
        }
    }

    /** FML sorts coremod candidates by file name, ignoring case, and examines them in that order. */
    private boolean examinedBeforeUs(Candidate candidate) {
        return selfInMods() && candidate.file.getName()
            .compareToIgnoreCase(self.getName()) < 0;
    }

    /**
     * Running from a jar in the mods folder: FML listed the folder before this ran. Otherwise (dev runs, command line
     * coremods) this runs before FML lists it.
     */
    private boolean selfInMods() {
        if (self == null || !self.isFile()) {
            return false;
        }
        File dir = self.getParentFile();
        return modsDir.equals(dir) || versionedModsDir.equals(dir);
    }

    private static boolean sameContent(File a, File b) throws IOException {
        return a.length() == b.length() && sha1(a).equals(sha1(b));
    }

    private static File freeDisabledName(File jar) {
        File dir = jar.getParentFile();
        File target = new File(dir, jar.getName() + DISABLED);
        String base = jar.getName()
            .substring(
                0,
                jar.getName()
                    .length() - ".jar".length());
        for (int n = 2; target.exists(); n++) {
            target = new File(dir, base + " (" + n + ").jar" + DISABLED);
        }
        return target;
    }

    // Resolving

    private Resolution resolve(List<ModDeclaration> declarations, List<Dep> deps, List<Candidate> candidates) {
        List<Resolution.Resolved> resolved = new ArrayList<>();
        List<Resolution.Missing> missing = new ArrayList<>();
        for (ModDeclaration mod : declarations) {
            Map<String, List<Dep>> groups = new LinkedHashMap<>();
            for (Dep dep : deps) {
                if (dep.owner == mod) {
                    String key = dep.declared.isAlternative() ? dep.declared.alternativeOf : dep.declared.id;
                    List<Dep> group = groups.get(key);
                    if (group == null) {
                        group = new ArrayList<>();
                        groups.put(key, group);
                    }
                    if (dep.declared.isAlternative()) {
                        group.add(dep);
                    } else {
                        group.add(0, dep);
                    }
                }
            }
            for (List<Dep> group : groups.values()) {
                DepDeclaration primary = group.get(0).declared;
                Resolution.Resolved found = null;
                Set<File> wrong = new LinkedHashSet<>();
                for (Dep dep : group) {
                    Candidate best = null;
                    Match bestMatch = Match.NONE;
                    for (Candidate candidate : candidates) {
                        Match m = match(dep, candidate);
                        if (m == Match.WRONG_VERSION) {
                            wrong.add(candidate.file);
                        } else if (m != Match.NONE && (best == null || m.ordinal() < bestMatch.ordinal()
                            || (m == bestMatch && candidate.place.ordinal() < best.place.ordinal()))) {
                                best = candidate;
                                bestMatch = m;
                            }
                    }
                    if (best != null) {
                        found = new Resolution.Resolved(
                            mod,
                            primary,
                            dep.declared,
                            best.file,
                            bestMatch == Match.EXACT);
                        break;
                    }
                }
                if (found != null) {
                    resolved.add(found);
                    LOG.info(
                        "{}/{}: {} ({})",
                        mod.modId,
                        found.chosen.id,
                        relative(found.file),
                        found.exact ? "sha1 match" : "mod id and version match");
                } else {
                    missing.add(new Resolution.Missing(mod, primary, new ArrayList<>(wrong)));
                    LOG.info(
                        "{}/{}: not found{}{}",
                        mod.modId,
                        primary.id,
                        primary.optional ? " (optional)" : "",
                        wrong.isEmpty() ? "" : ", wrong version: " + wrong);
                }
            }
        }
        return result(declarations, resolved, missing);
    }

    // Helpers

    private static List<File> jarsIn(File... dirs) {
        List<File> jars = new ArrayList<>();
        for (File dir : dirs) {
            File[] files = dir.listFiles();
            if (files == null) {
                continue;
            }
            Arrays.sort(files);
            for (File file : files) {
                if (file.isFile() && file.getName()
                    .endsWith(".jar")) {
                    jars.add(file);
                }
            }
        }
        return jars;
    }

    private String relative(File file) {
        File parent = modsDir.getParentFile();
        if (parent != null && file.getPath()
            .startsWith(parent.getPath() + File.separator)) {
            return file.getPath()
                .substring(
                    parent.getPath()
                        .length() + 1);
        }
        return file.getPath();
    }

    private static File canonical(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    static String sha1(File file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[65536];
        try (InputStream in = Files.newInputStream(file.toPath())) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private enum Place {
        /** An enabled jar in the mods folder: FML would load it. */
        MODS,
        MODS_DISABLED,
        STORE
    }

    /** Best first. */
    private enum Match {
        EXACT,
        LOOSE,
        WRONG_VERSION,
        NONE
    }

    private static final class Candidate {

        File file;
        Place place;
        /** The file name without .disabled, to compare with a dep's file name. */
        final String baseName;
        JarInfo info;
        String sha1;

        Candidate(File file, Place place) {
            this.file = file;
            this.place = place;
            String name = file.getName();
            this.baseName = name.endsWith(DISABLED) ? name.substring(0, name.length() - DISABLED.length()) : name;
        }
    }

    private static final class Dep {

        final ModDeclaration owner;
        final DepDeclaration declared;
        final List<Glob> includes = new ArrayList<>();
        final Set<String> specificRoots = new LinkedHashSet<>();
        final VersionRange range;
        final boolean versionsInvalid;

        Dep(ModDeclaration owner, DepDeclaration declared) {
            this.owner = owner;
            this.declared = declared;
            for (String include : declared.include) {
                Glob glob = new Glob(include);
                includes.add(glob);
                if (glob.isSpecific()) {
                    specificRoots.add(glob.root());
                }
            }
            VersionRange parsed = null;
            boolean invalid = false;
            if (!declared.versions.isEmpty()) {
                try {
                    parsed = VersionRange.createFromVersionSpec(declared.versions);
                } catch (InvalidVersionSpecificationException e) {
                    invalid = true;
                    LOG.warn("{}/{}: can't parse versions {}: {}", owner.modId, declared.id, declared.versions, e);
                }
            }
            this.range = parsed;
            this.versionsInvalid = invalid;
        }

        /** Without a file name, mod id or narrow include, only hashing every jar can find this dep. */
        boolean unrecognizable() {
            return !declared.sha1.isEmpty() && declared.file.isEmpty()
                && declared.modId.isEmpty()
                && specificRoots.isEmpty();
        }
    }

}

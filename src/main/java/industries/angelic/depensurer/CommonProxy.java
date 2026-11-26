package industries.angelic.depensurer;

import java.util.LinkedHashSet;
import java.util.Set;

import cpw.mods.fml.common.MissingModsException;
import cpw.mods.fml.common.versioning.ArtifactVersion;
import cpw.mods.fml.common.versioning.DefaultArtifactVersion;
import cpw.mods.fml.common.versioning.VersionRange;
import industries.angelic.depensurer.core.Resolution;

public class CommonProxy {

    /** Serves the found deps. The dedicated server has no resource packs. */
    public void serve(Resolution resolution) {}

    /** Stops loading. The report is already in the log. */
    public void fail(Report report) {
        if (report.missing.isEmpty()) {
            throw new IllegalStateException("Dep Ensurer: " + String.join(" ", report.lines()));
        }
        Set<ArtifactVersion> missing = new LinkedHashSet<>();
        for (Report.Entry entry : report.missing) {
            // Not DefaultArtifactVersion(label, version): its range points back at it, and toString() recurses forever
            missing.add(
                new DefaultArtifactVersion(
                    entry.name + " (needed by " + entry.neededBy + ")",
                    VersionRange.createFromVersion(entry.file.isEmpty() ? "see the log" : entry.file, null)));
        }
        throw new MissingModsException(missing);
    }
}

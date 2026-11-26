package industries.angelic.depensurer.api;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import industries.angelic.depensurer.Flavor;
import industries.angelic.depensurer.core.Resolution;

/** Runtime access to the jars declared with {@link EnsureDep}. Usable from construction on. */
public final class DepEnsurerAPI {

    private DepEnsurerAPI() {}

    /** True in the CurseForge build, which never downloads anything. */
    public static boolean isCurseForgeBuild() {
        return Flavor.isCurseForge();
    }

    /** Whether a dep of the given mod was found, by the id of the dep or of the alternative in use. */
    public static boolean isPresent(String modId, String depId) {
        return getFile(modId, depId) != null;
    }

    /** The jar serving a dep, or null when it's missing. Its name may end in .jar.disabled. */
    public static File getFile(String modId, String depId) {
        Resolution resolution = Resolution.current();
        Resolution.Resolved resolved = resolution == null ? null : resolution.find(modId, depId);
        return resolved == null ? null : resolved.file;
    }

    /**
     * Opens an entry of a dep's jar by its path inside the jar, ignoring include, exclude and remap. Null when the dep
     * or the entry is missing. Closing the stream closes the jar.
     */
    public static InputStream open(String modId, String depId, String entry) throws IOException {
        File file = getFile(modId, depId);
        if (file == null) {
            return null;
        }
        final ZipFile zip = new ZipFile(file);
        ZipEntry zipEntry = zip.getEntry(entry);
        if (zipEntry == null) {
            zip.close();
            return null;
        }
        return new FilterInputStream(zip.getInputStream(zipEntry)) {

            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    zip.close();
                }
            }
        };
    }
}

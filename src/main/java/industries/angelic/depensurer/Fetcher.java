package industries.angelic.depensurer;

import java.io.File;
import java.io.IOException;

import net.minecraftforge.common.config.Configuration;

import industries.angelic.depensurer.declaration.DepDeclaration;

/**
 * Gets a missing dep's jar. Only the full build has an implementation; the CurseForge build has no network code at all
 * and leaves missing jars to the player.
 */
public interface Fetcher {

    /** Reads its options from Dep Ensurer's config, adding them when missing. */
    void configure(Configuration config);

    /** False when the player turned downloads off. */
    boolean enabled();

    /** Whether the dep has anything this can download and check: a source and a sha1. */
    boolean canFetch(DepDeclaration dep);

    /** Downloads the dep's jar into the store and checks its sha1. Returns the jar. */
    File fetch(DepDeclaration dep, File store) throws IOException;
}

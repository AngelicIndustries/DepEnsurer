package industries.angelic.depensurer;

import java.util.Map;

import net.minecraftforge.common.config.Configuration;

import industries.angelic.depensurer.core.Resolution;

/**
 * Gets missing jars at construction. Only the full build has an implementation; the CurseForge build has none and no
 * network code at all, and leaves missing jars to the player.
 */
public interface Fetcher {

    /** Reads its options from Dep Ensurer's config, adding them when missing. */
    void configure(Configuration config);

    /**
     * Tries to get what's missing and returns the resolution with what it got.
     *
     * @param dedicatedServer skip deps only the client needs
     * @param notes           receives, per group still missing, a line for the player saying why
     */
    Resolution fetchMissing(Resolution resolution, boolean dedicatedServer, Map<Resolution.Missing, String> notes);
}

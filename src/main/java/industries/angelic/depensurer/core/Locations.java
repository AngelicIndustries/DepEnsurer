package industries.angelic.depensurer.core;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Where Dep Ensurer keeps jars outside the mods folder. */
public final class Locations {

    private Locations() {}

    /**
     * The shared store when {@code -Dminecraft.sharedDataDir} or {@code MINECRAFT_SHARED_DATA_DIR} is set (the
     * convention Asset Director and FalsePatternLib follow), otherwise {@code depensurer/jars} in the game folder.
     */
    public static File store(File gameDir) {
        String shared = System.getProperty("minecraft.sharedDataDir");
        if (shared == null || shared.isEmpty()) {
            shared = System.getenv("MINECRAFT_SHARED_DATA_DIR");
        }
        File base = shared == null || shared.isEmpty() ? gameDir : new File(shared);
        return new File(base, "depensurer" + File.separator + "jars");
    }

    /** Every folder to look for jars in besides the mods folder: the game folder's store and the shared one. */
    public static List<File> stores(File gameDir) {
        List<File> stores = new ArrayList<>();
        stores.add(new File(gameDir, "depensurer" + File.separator + "jars"));
        File store = store(gameDir);
        if (!stores.contains(store)) {
            stores.add(store);
        }
        return stores;
    }
}

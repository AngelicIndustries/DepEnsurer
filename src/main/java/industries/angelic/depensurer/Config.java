package industries.angelic.depensurer;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static Configuration config;

    // debug
    public static boolean debugMode = false;

    public static class Categories {

        public static final String debug = "debug";
    }

    public static void loadConfig(File configFile) {
        config = new Configuration(configFile);

        try {
            config.load();

            // debug
            debugMode = config.getBoolean("debugMode", Categories.debug, debugMode, "Enable debug logging.");
        } catch (Exception e) {
            DepEnsurer.LOG.error("Failed to load config", e);
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static Configuration getRawConfig() {
        return config;
    }

    public static void save() {
        if (config != null) {
            config.save();
        }
    }
}

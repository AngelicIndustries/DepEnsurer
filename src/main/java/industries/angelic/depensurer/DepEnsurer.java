package industries.angelic.depensurer;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLConstructionEvent;

@Mod(
    modid = DepEnsurer.MODID,
    name = "Dep Ensurer",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*",
    guiFactory = "industries.angelic.depensurer.gui.GuiFactory",
    customProperties = { @Mod.CustomProperty(k = "license", v = "LGPLv3"),
        @Mod.CustomProperty(k = "issueTrackerUrl", v = "https://github.com/AngelicIndustries/DepEnsurer/issues") })
public class DepEnsurer {

    public static final String MODID = "depensurer";
    public static final String MODGROUP = "industries.angelic";
    public static final Logger LOG = LogManager.getLogger("DepEnsurer");

    public static boolean DEBUG_MODE;

    public static File confFile;

    @SidedProxy(
        clientSide = MODGROUP + "." + MODID + ".ClientProxy",
        serverSide = MODGROUP + "." + MODID + ".CommonProxy")
    public static CommonProxy proxy;

    // Everything happens at construction: FML refreshes resources right after it, and only turns a
    // MissingModsException into its missing mods screen when it is thrown here.
    @Mod.EventHandler
    public void construct(FMLConstructionEvent event) {
        DEBUG_MODE = System.getenv("MCMODDING_DEBUG_MODE") != null;
        confFile = new File(
            Loader.instance()
                .getConfigDir(),
            MODID + ".cfg");
        Config.loadConfig(confFile);
        debug("Debug mode on");
        proxy.construct(event);
    }

    public static void debug(String message) {
        if (DEBUG_MODE || Config.debugMode) {
            LOG.info("DEBUG: " + message);
        }
    }
}

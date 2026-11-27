package industries.angelic.depensurer;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.discovery.ASMDataTable;
import cpw.mods.fml.common.event.FMLConstructionEvent;
import cpw.mods.fml.relauncher.Side;
import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.api.EnsureDeps;
import industries.angelic.depensurer.core.Resolution;
import industries.angelic.depensurer.declaration.ModDeclaration;

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

        Resolution resolution = Resolution.current();
        if (resolution == null) {
            resolution = Resolution.notRun();
        }
        checkAnnotations(event.getASMHarvestedData(), resolution);
        boolean dedicated = event.getSide() == Side.SERVER;
        Map<Resolution.Missing, String> notes = new HashMap<>();
        Fetcher fetcher = Fetchers.find();
        if (fetcher != null) {
            fetcher.configure(Config.getRawConfig());
            if (Config.getRawConfig()
                .hasChanged()) {
                Config.save();
            }
            resolution = fetcher.fetchMissing(resolution, dedicated, notes);
            Resolution.update(resolution);
        }
        describe(resolution);
        Report report = Report.of(resolution, dedicated, DepEnsurer::modName, notes);
        if (!report.isEmpty()) {
            for (String line : report.lines()) {
                LOG.error(line);
            }
            proxy.fail(report);
        }
        proxy.serve(resolution);
    }

    /** A mod with @EnsureDep but no depensurer.json was built without the annotation processor. */
    private static void checkAnnotations(ASMDataTable asm, Resolution resolution) {
        Set<String> declared = new HashSet<>();
        for (ModDeclaration mod : resolution.declarations) {
            declared.add(mod.className);
        }
        Set<String> reported = new HashSet<>();
        for (String annotation : Arrays.asList(EnsureDep.class.getName(), EnsureDeps.class.getName())) {
            for (ASMDataTable.ASMData data : asm.getAll(annotation)) {
                String className = data.getClassName();
                if (!declared.contains(className) && reported.add(className)) {
                    LOG.error(
                        "{} has @EnsureDep but its jar has no META-INF/depensurer.json, so Dep Ensurer ignores it. Its author has to add annotationProcessor(\"industries.angelic.depensurer:DepEnsurer:<version>:api\").",
                        className);
                }
            }
        }
    }

    /** Lists the jars in use in the mods list, so players can see what Dep Ensurer did. */
    private static void describe(Resolution resolution) {
        ModContainer self = Loader.instance()
            .getIndexedModList()
            .get(MODID);
        if (self == null || resolution.resolved.isEmpty()) {
            return;
        }
        ModMetadata metadata = self.getMetadata();
        StringBuilder description = new StringBuilder(metadata.description == null ? "" : metadata.description);
        description.append("\n\nAsset sources in use:");
        for (Resolution.Resolved resolved : resolution.resolved) {
            description.append("\n")
                .append(modName(resolved.owner.modId))
                .append(": ")
                .append(resolved.file.getName());
        }
        metadata.description = description.toString();
    }

    private static String modName(String modId) {
        ModContainer mod = Loader.instance()
            .getIndexedModList()
            .get(modId);
        return mod == null ? modId : mod.getName();
    }

    public static void debug(String message) {
        if (DEBUG_MODE || Config.debugMode) {
            LOG.info("DEBUG: " + message);
        }
    }
}

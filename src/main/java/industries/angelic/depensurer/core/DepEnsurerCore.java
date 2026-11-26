package industries.angelic.depensurer.core;

import java.io.File;
import java.net.URL;
import java.util.Map;

import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

// FML sorts coremod candidates by file name and loads them in that order, so the "+" at the start of the jar name
// makes this run before the coremods of the jars it neutralizes. FML doesn't catch exceptions thrown here.
@IFMLLoadingPlugin.Name("DepEnsurerCore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.TransformerExclusions("industries.angelic.depensurer.core")
public class DepEnsurerCore implements IFMLLoadingPlugin {

    public static final Logger LOG = LogManager.getLogger("DepEnsurerCore");

    public DepEnsurerCore() {
        long start = System.nanoTime();
        try {
            File gameDir = Launch.minecraftHome == null ? new File(".") : Launch.minecraftHome;
            Resolution.setCurrent(
                new CoreScan(new File(gameDir, "mods"), Locations.stores(gameDir), ownLocation(), Launch.classLoader)
                    .run());
        } catch (Throwable t) {
            LOG.error("Scan failed", t);
            Resolution.setCurrent(Resolution.failed(t));
        }
        LOG.info("Scan took {} ms", (System.nanoTime() - start) / 1_000_000);
    }

    /** This jar, or the class folder in dev runs. Null if it can't be told. */
    private static File ownLocation() {
        try {
            URL url = DepEnsurerCore.class.getResource("DepEnsurerCore.class");
            String location = url.toString();
            if (location.startsWith("jar:")) {
                return new File(new URL(location.substring(4, location.indexOf("!/"))).toURI());
            }
            String classFile = DepEnsurerCore.class.getName()
                .replace('.', '/') + ".class";
            if (location.startsWith("file:") && location.endsWith(classFile)) {
                return new File(new URL(location.substring(0, location.length() - classFile.length())).toURI());
            }
        } catch (Exception e) {
            LOG.warn("Can't tell where Dep Ensurer's jar is: {}", e.toString());
        }
        return null;
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

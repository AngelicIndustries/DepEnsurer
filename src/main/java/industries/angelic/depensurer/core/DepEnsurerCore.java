package industries.angelic.depensurer.core;

import java.util.Map;

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
        LOG.info("Dep Ensurer core loaded");
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

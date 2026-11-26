package depensurer.testtarget;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/** FML doesn't catch exceptions from a coremod constructor, so this stops the launch if the jar wasn't disabled. */
@IFMLLoadingPlugin.MCVersion("1.7.10")
public class TestTargetCore implements IFMLLoadingPlugin {

    public TestTargetCore() {
        throw new IllegalStateException("Dep Ensurer test target's coremod was loaded: neutralizing failed");
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

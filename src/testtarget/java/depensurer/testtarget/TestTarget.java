package depensurer.testtarget;

import cpw.mods.fml.common.Mod;

/** Stands in for an asset source. Loading it as a mod means neutralizing failed, so it fails loudly. */
@Mod(modid = "depensurertarget", name = "Dep Ensurer test target", version = "1.0")
public class TestTarget {

    public TestTarget() {
        throw new IllegalStateException("Dep Ensurer test target was loaded as a mod: neutralizing failed");
    }
}

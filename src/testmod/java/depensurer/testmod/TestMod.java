package depensurer.testmod;

import cpw.mods.fml.common.Mod;
import industries.angelic.depensurer.api.EnsureDep;

/** Declares the test target as an asset source, the way a real mod would. */
@Mod(
    modid = "depensurertest",
    name = "Dep Ensurer test mod",
    version = "1.0",
    dependencies = "required-after:depensurer")
@EnsureDep(
    id = "target",
    name = "Dep Ensurer test target",
    file = "depensurer-testtarget.jar",
    modId = "depensurertarget",
    versions = "[1.0,2.0)",
    include = "assets/depensurertarget/**",
    remap = "assets/depensurertarget/ -> assets/depensurertest/")
public class TestMod {
}

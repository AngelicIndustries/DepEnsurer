package depensurer.testmod;

import java.io.IOException;
import java.io.InputStream;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import industries.angelic.depensurer.api.CurseForgeFile;
import industries.angelic.depensurer.api.DepEnsurerAPI;
import industries.angelic.depensurer.api.EnsureDep;
import industries.angelic.depensurer.api.ModrinthFile;

/**
 * Declares the test target as an asset source, the way a real mod would, and shows a served texture: the marker
 * item's texture only exists in the target jar, remapped into this mod's domain.
 */
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
    remap = "assets/depensurertarget/ -> assets/depensurertest/",
    // Placeholder pages, so the missing files screen has links to draw
    curseforge = @CurseForgeFile(project = 1, slug = "depensurer-test-target", file = 1),
    modrinth = @ModrinthFile(project = "depensurer-test-target", version = "1.0"))
public class TestMod {

    private static final Logger LOG = LogManager.getLogger("DepEnsurerTest");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        GameRegistry.registerItem(
            new Item().setUnlocalizedName("depensurertest.marker")
                .setTextureName("depensurertest:marker")
                .setCreativeTab(CreativeTabs.tabMisc),
            "marker");

        LOG.info(
            "API: present {}, file {}, CurseForge build {}",
            DepEnsurerAPI.isPresent("depensurertest", "target"),
            DepEnsurerAPI.getFile("depensurertest", "target"),
            DepEnsurerAPI.isCurseForgeBuild());
        try (InputStream in = DepEnsurerAPI
            .open("depensurertest", "target", "assets/depensurertarget/textures/items/marker.png")) {
            LOG.info("API: marker.png has {} bytes", in == null ? -1 : in.available());
        } catch (IOException e) {
            LOG.error("API: can't open marker.png", e);
        }
    }
}

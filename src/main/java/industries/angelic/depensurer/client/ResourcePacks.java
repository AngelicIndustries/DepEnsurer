package industries.angelic.depensurer.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;

import cpw.mods.fml.common.ObfuscationReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import industries.angelic.depensurer.DepEnsurer;
import industries.angelic.depensurer.core.Resolution;

@SideOnly(Side.CLIENT)
public final class ResourcePacks {

    private ResourcePacks() {}

    /**
     * Adds a pack per found dep to Minecraft's default packs, right after vanilla's: mod jars and the player's resource
     * packs still override them. Called at construction, before FML's first resource reload.
     */
    public static void inject(Resolution resolution) {
        List<IResourcePack> packs = new ArrayList<>();
        for (Resolution.Resolved resolved : resolution.resolved) {
            String id = resolved.owner.modId + "/" + resolved.chosen.id;
            try {
                JarResourcePack pack = new JarResourcePack(resolved.file, resolved.chosen, "Dep Ensurer: " + id);
                DepEnsurer.LOG.info(
                    "Serving {} files from {} for {}, domains {}",
                    pack.assets()
                        .size(),
                    resolved.file.getName(),
                    id,
                    pack.getResourceDomains());
                for (String collision : pack.assets()
                    .collisions()) {
                    DepEnsurer.LOG.warn("{}: {} remaps onto a path that's already taken, skipping it", id, collision);
                }
                packs.add(pack);
            } catch (IOException e) {
                DepEnsurer.LOG.error("Can't open {} for {}", resolved.file, id, e);
            }
        }
        if (packs.isEmpty()) {
            return;
        }
        List<IResourcePack> defaults = ObfuscationReflectionHelper
            .getPrivateValue(Minecraft.class, Minecraft.getMinecraft(), "defaultResourcePacks", "field_110449_ao");
        defaults.addAll(Math.min(1, defaults.size()), packs);
    }
}

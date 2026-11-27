package industries.angelic.depensurer.gui;

import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;

import com.google.common.collect.Lists;

import cpw.mods.fml.client.IModGuiFactory;
import cpw.mods.fml.client.config.GuiConfig;
import cpw.mods.fml.client.config.IConfigElement;
import industries.angelic.depensurer.Config;
import industries.angelic.depensurer.DepEnsurer;

@SuppressWarnings("unused")
public class GuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraftInstance) {}

    @Override
    public Class<? extends GuiScreen> mainConfigGuiClass() {
        return ConfigGui.class;
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }

    @Override
    public RuntimeOptionGuiHandler getHandlerFor(RuntimeOptionCategoryElement element) {
        return null;
    }

    public static class ConfigGui extends GuiConfig {

        public ConfigGui(GuiScreen parentScreen) {
            super(parentScreen, createRootElements(), DepEnsurer.MODID, DepEnsurer.MODID, false, false, "Dep Ensurer");
        }

        @Override
        protected void actionPerformed(GuiButton b) {
            super.actionPerformed(b);
            /* "Done" button */
            if (b.id == 2000) {
                Config.save();
                Config.loadConfig(DepEnsurer.confFile);
            }
        }

        /** Every category: the full build's downloader adds its own. */
        private static List<IConfigElement> createRootElements() {
            List<IConfigElement> elements = Lists.newArrayList();
            for (String name : Config.getRawConfig()
                .getCategoryNames()) {
                elements.add(
                    new ConfigElement(
                        Config.getRawConfig()
                            .getCategory(name)));
            }
            return elements;
        }
    }
}

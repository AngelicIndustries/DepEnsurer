package industries.angelic.depensurer.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;

import cpw.mods.fml.client.CustomModLoadingErrorDisplayException;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import industries.angelic.depensurer.Report;

/**
 * Stops loading and shows {@link MissingFilesScreen}. FML's error screen only offers drawing, and any button on it
 * closes it into a half-loaded game, so this swaps in the screen on the first frame.
 */
@SideOnly(Side.CLIENT)
public class MissingFilesException extends CustomModLoadingErrorDisplayException {

    private static final long serialVersionUID = 1L;

    private final transient Report report;
    private boolean shown;

    public MissingFilesException(Report report) {
        super("Dep Ensurer: " + String.join("\n", report.lines()), null);
        this.report = report;
    }

    @Override
    public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {}

    @Override
    public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY,
        float tickTime) {
        if (!shown) {
            shown = true;
            Minecraft.getMinecraft()
                .displayGuiScreen(new MissingFilesScreen(report));
        }
    }
}

package industries.angelic.depensurer.client;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import industries.angelic.depensurer.DepEnsurer;

/** Opens a web page or a folder with the system's handler. */
@SideOnly(Side.CLIENT)
final class Browser {

    private Browser() {}

    static void openUrl(String url) {
        String os = os();
        if (os.contains("win")) {
            run("rundll32", "url.dll,FileProtocolHandler", url);
        } else if (os.contains("mac")) {
            run("open", url);
        } else {
            run("xdg-open", url);
        }
    }

    static void openFolder(File folder) {
        folder.mkdirs();
        String path = folder.getAbsolutePath();
        String os = os();
        if (os.contains("win")) {
            run("explorer", path);
        } else if (os.contains("mac")) {
            run("open", path);
        } else {
            run("xdg-open", path);
        }
    }

    private static String os() {
        return System.getProperty("os.name", "")
            .toLowerCase(Locale.ROOT);
    }

    private static void run(String... command) {
        try {
            new ProcessBuilder(command).start();
        } catch (IOException e) {
            DepEnsurer.LOG.warn("Can't open {}: {}", command[command.length - 1], e.toString());
        }
    }
}

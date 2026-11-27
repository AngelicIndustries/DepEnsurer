package industries.angelic.depensurer.client;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import industries.angelic.depensurer.Report;

/**
 * Laid out like FML's missing mods screen: what's missing, who needs it and where to get it. There's no way back into
 * the game from here, only to the mods folder or out.
 */
@SideOnly(Side.CLIENT)
public class MissingFilesScreen extends GuiScreen {

    private static final int WHITE = 0xFFFFFF;
    private static final int TEXT = 0xEEEEEE;
    private static final int GRAY = 0xAAAAAA;
    private static final int WARN = 0xFF8888;
    private static final int LINK = 0x55AAFF;
    private static final int LINK_HOVER = 0xAAD4FF;
    private static final int LINE = 10;

    private final Report report;
    private final List<LinkArea> linkAreas = new ArrayList<>();

    public MissingFilesScreen(Report report) {
        this.report = report;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int y = height - 28;
        buttonList.add(
            new GuiButton(0, width / 2 - 154, y, 150, 20, text("depensurer.missing.openMods", "Open mods folder")));
        buttonList.add(new GuiButton(1, width / 2 + 4, y, 150, 20, text("depensurer.missing.quit", "Quit game")));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        linkAreas.clear();
        List<Row> rows = rows(height - 40);
        int contentHeight = 0;
        for (Row row : rows) {
            contentHeight += row.height;
        }
        int y = Math.max(10, (height - 40 - contentHeight) / 2);
        for (Row row : rows) {
            draw(row, y, mouseX, mouseY);
            y += row.height;
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    /** The screen's rows, leaving out missing files that don't fit in the given height. */
    private List<Row> rows(int available) {
        int maxWidth = width - 20;
        List<Row> head = new ArrayList<>();
        head.add(
            Row.plain(
                text("depensurer.missing.title", "Dep Ensurer has found a problem with your Minecraft installation"),
                WHITE));
        if (!report.missing.isEmpty()) {
            wrap(
                head,
                text(
                    "depensurer.missing.files",
                    "The files listed below are needed. Download them, put them in the mods folder and restart the game."),
                WHITE,
                maxWidth);
        } else if (report.restart) {
            wrap(head, text("depensurer.missing.restart", "Restart the game."), WHITE, maxWidth);
        }
        head.add(Row.space(6));

        List<Row> tail = new ArrayList<>();
        for (String problem : report.problems) {
            wrap(tail, problem, WARN, maxWidth);
        }
        tail.add(Row.space(10));
        tail.add(
            Row.plain(
                text("depensurer.missing.log", "The file 'logs/fml-client-latest.log' contains more information"),
                WHITE));

        int used = height(head) + height(tail);
        List<Row> entries = new ArrayList<>();
        int shown = 0;
        for (Report.Entry entry : report.missing) {
            List<Row> rows = entryRows(entry, maxWidth);
            boolean last = shown == report.missing.size() - 1;
            if (used + height(rows) + (last ? 0 : LINE) > available) {
                break;
            }
            entries.addAll(rows);
            used += height(rows);
            shown++;
        }
        if (shown < report.missing.size()) {
            entries.add(
                Row.plain(
                    text(
                        "depensurer.missing.more",
                        "and %s more, listed in logs/fml-client-latest.log",
                        report.missing.size() - shown),
                    GRAY));
        }

        List<Row> rows = new ArrayList<>(head);
        rows.addAll(entries);
        rows.addAll(tail);
        return rows;
    }

    private List<Row> entryRows(Report.Entry entry, int maxWidth) {
        List<Row> rows = new ArrayList<>();
        Row title = new Row(LINE);
        title.add(entry.name, TEXT, null);
        if (!entry.file.isEmpty() && !entry.file.equals(entry.name)) {
            title.add(" - " + entry.file, GRAY, null);
        }
        rows.add(title);

        Row neededBy = new Row(LINE);
        neededBy.add(text("depensurer.missing.neededBy", "needed by %s", entry.neededBy), GRAY, null);
        if (!entry.wrongVersions.isEmpty()) {
            neededBy.add(", ", GRAY, null);
            neededBy.add(
                text(
                    "depensurer.missing.wrongVersion",
                    "wrong version installed: %s",
                    String.join(", ", entry.wrongVersions)),
                WARN,
                null);
        }
        rows.add(neededBy);
        if (entry.downloadError != null) {
            wrap(
                rows,
                text("depensurer.missing.downloadFailed", "download failed: %s", entry.downloadError),
                WARN,
                maxWidth);
        }

        Row links = new Row(LINE);
        if (entry.links.isEmpty()) {
            links.add(text("depensurer.missing.noLink", "no download page known"), GRAY, null);
        }
        for (int i = 0; i < entry.links.size(); i++) {
            if (i > 0) {
                links.add("    ", GRAY, null);
            }
            Report.Link link = entry.links.get(i);
            links.add(link.label, LINK, link.url);
        }
        rows.add(links);
        rows.add(Row.space(4));
        return rows;
    }

    private void wrap(List<Row> rows, String text, int color, int maxWidth) {
        for (Object line : fontRendererObj.listFormattedStringToWidth(text, maxWidth)) {
            rows.add(Row.plain((String) line, color));
        }
    }

    private static int height(List<Row> rows) {
        int height = 0;
        for (Row row : rows) {
            height += row.height;
        }
        return height;
    }

    private void draw(Row row, int y, int mouseX, int mouseY) {
        int total = 0;
        for (Segment segment : row.segments) {
            total += fontRendererObj.getStringWidth(segment.text);
        }
        int x = (width - total) / 2;
        for (Segment segment : row.segments) {
            int w = fontRendererObj.getStringWidth(segment.text);
            int color = segment.color;
            if (segment.url != null) {
                LinkArea area = new LinkArea(x, y - 1, w, LINE, segment.url);
                linkAreas.add(area);
                if (area.contains(mouseX, mouseY)) {
                    color = LINK_HOVER;
                }
                drawHorizontalLine(x, x + w - 1, y + 8, 0xFF000000 | color);
            }
            fontRendererObj.drawStringWithShadow(segment.text, x, y, color);
            x += w;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
        if (button != 0) {
            return;
        }
        for (LinkArea area : linkAreas) {
            if (area.contains(mouseX, mouseY)) {
                Browser.openUrl(area.url);
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            Browser.openFolder(new File(mc.mcDataDir, "mods"));
        } else if (button.id == 1) {
            mc.shutdown();
        }
    }

    /** No Escape: closing this would drop the player into a game that never finished loading. */
    @Override
    protected void keyTyped(char typedChar, int keyCode) {}

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** The translation, or the English fallback while the language files aren't loaded. */
    private static String text(String key, String fallback, Object... args) {
        String translated = I18n.format(key, args);
        return translated.equals(key) ? String.format(fallback, args) : translated;
    }

    private static final class Row {

        final int height;
        final List<Segment> segments = new ArrayList<>();

        Row(int height) {
            this.height = height;
        }

        static Row plain(String text, int color) {
            Row row = new Row(LINE);
            row.add(text, color, null);
            return row;
        }

        static Row space(int height) {
            return new Row(height);
        }

        void add(String text, int color, String url) {
            segments.add(new Segment(text, color, url));
        }
    }

    private static final class Segment {

        final String text;
        final int color;
        /** Null for plain text. */
        final String url;

        Segment(String text, int color, String url) {
            this.text = text;
            this.color = color;
            this.url = url;
        }
    }

    private static final class LinkArea {

        final int x, y, width, height;
        final String url;

        LinkArea(int x, int y, int width, int height, String url) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.url = url;
        }

        boolean contains(int px, int py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }
}

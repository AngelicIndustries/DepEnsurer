package industries.angelic.depensurer.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import com.google.gson.JsonObject;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import industries.angelic.depensurer.declaration.DepDeclaration;
import industries.angelic.depensurer.util.AssetMap;

/** Serves a dep's included assets straight from its jar, under their remapped paths. Nothing is extracted. */
@SideOnly(Side.CLIENT)
public class JarResourcePack implements IResourcePack {

    private final ZipFile zip;
    private final AssetMap assets;
    private final String name;

    public JarResourcePack(File file, DepDeclaration dep, String name) throws IOException {
        this.zip = new ZipFile(file);
        List<String> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            entries.add(
                e.nextElement()
                    .getName());
        }
        this.assets = new AssetMap(entries, dep.include, dep.exclude, dep.remap);
        this.name = name;
    }

    public AssetMap assets() {
        return assets;
    }

    private String jarPath(ResourceLocation location) {
        return assets.jarPath("assets/" + location.getResourceDomain() + "/" + location.getResourcePath());
    }

    @Override
    public InputStream getInputStream(ResourceLocation location) throws IOException {
        String path = jarPath(location);
        if (path == null) {
            throw new FileNotFoundException(location.toString());
        }
        return zip.getInputStream(zip.getEntry(path));
    }

    @Override
    public boolean resourceExists(ResourceLocation location) {
        return jarPath(location) != null;
    }

    @Override
    public Set<String> getResourceDomains() {
        return assets.domains();
    }

    @Override
    public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String section) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", 1);
        pack.addProperty("description", name);
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        return serializer.parseMetadataSection(section, root);
    }

    @Override
    public BufferedImage getPackImage() throws IOException {
        throw new FileNotFoundException("pack.png");
    }

    @Override
    public String getPackName() {
        return name;
    }
}

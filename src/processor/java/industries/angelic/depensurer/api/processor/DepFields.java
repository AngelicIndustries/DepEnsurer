package industries.angelic.depensurer.api.processor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import industries.angelic.depensurer.api.EnsureDep;

/** One dep's values as declared, from an {@link EnsureDep} or from gradle.properties. Defaults are the annotation's. */
final class DepFields {

    String id = "";
    String name = "";
    String file = "";
    List<String> sha1 = new ArrayList<>();
    String modId = "";
    String versions = "";
    String alternativeOf = "";
    int curseForgeProject;
    String curseForgeSlug = "";
    int curseForgeFile;
    String modrinthProject = "";
    String modrinthVersion = "";
    List<String> urls = new ArrayList<>();
    List<String> include = new ArrayList<>(Arrays.asList("assets/**"));
    List<String> exclude = new ArrayList<>();
    List<String> remap = new ArrayList<>();
    boolean neutralize = true;
    String side = EnsureDep.Side.CLIENT.name();
    boolean optional;

    static DepFields of(EnsureDep dep) {
        DepFields fields = new DepFields();
        fields.id = dep.id();
        fields.name = dep.name();
        fields.file = dep.file();
        fields.sha1 = new ArrayList<>(Arrays.asList(dep.sha1()));
        fields.modId = dep.modId();
        fields.versions = dep.versions();
        fields.alternativeOf = dep.alternativeOf();
        fields.curseForgeProject = dep.curseforge()
            .project();
        fields.curseForgeSlug = dep.curseforge()
            .slug();
        fields.curseForgeFile = dep.curseforge()
            .file();
        fields.modrinthProject = dep.modrinth()
            .project();
        fields.modrinthVersion = dep.modrinth()
            .version();
        fields.urls = new ArrayList<>(Arrays.asList(dep.urls()));
        fields.include = new ArrayList<>(Arrays.asList(dep.include()));
        fields.exclude = new ArrayList<>(Arrays.asList(dep.exclude()));
        fields.remap = new ArrayList<>(Arrays.asList(dep.remap()));
        fields.neutralize = dep.neutralize();
        fields.side = dep.side()
            .name();
        fields.optional = dep.optional();
        return fields;
    }
}

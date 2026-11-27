# Dep Ensurer

Lets Minecraft 1.7.10 mods use assets from other mods' jars without redistributing them.

[![hub](images/badges/github.png)](https://github.com/AngelicIndustries/DepEnsurer/releases)
![forge](images/badges/forge.png)
[![cord](images/badges/cord.png)](https://discord.gg/xAWCqGrguG)

<!-- Not published yet
[![curse](images/badges/curse.png)](https://www.curseforge.com/minecraft/mc-mods/dep-ensurer)
[![modrinth](images/badges/modrinth.png)](https://modrinth.com/mod/dep-ensurer)
[![67](images/badges/67.png)](https://67minecraft.com/mod/dep-ensurer)
[![maven](images/badges/maven.png)](https://maven.angelic.industries/#/releases/industries/angelic/depensurer/DepEnsurer)
-->

## Features

* A mod declares the jars it takes assets from; the player gets them from their official source.
* Those jars never load as mods: Dep Ensurer disables them before Forge scans them and serves the declared assets.
* The CurseForge build only uses jars the CurseForge app or the player installed, and lists missing ones with download
  links. The other builds also download them.

## Using it in a mod

Depend on it with `required-after:depensurer` in `@Mod`, and declare the jars, either way:

In `gradle.properties`, with the script applied in `build.gradle.kts`:

```kotlin
apply(from = "https://raw.githubusercontent.com/AngelicIndustries/DepEnsurer/${property("depensurer.version")}/depensurer.gradle.kts")
```

```properties
depensurer.version = 0.0.1
depensurer.deps = legacy
depensurer.legacy.name = The Aether v1.1.2.3
depensurer.legacy.file = aether-1.7.10-v1.1.2.3.jar
depensurer.legacy.sha1 = d45466de894c24fb2c7895704509073ea97eaf69
depensurer.legacy.modrinth = YhmgMVyu:4NuI8eHN
depensurer.legacy.curseforge = 255308:aether-legacy:5028330
depensurer.legacy.include = assets/aether_legacy/textures/**; assets/aether_legacy/sounds/**
```

Or with `@EnsureDep` on the `@Mod` class, adding the api jar as `compileOnly` and `annotationProcessor`
(`industries.angelic.depensurer:DepEnsurer:<version>:api`):

```java
@EnsureDep(
    id = "legacy",
    name = "The Aether v1.1.2.3",
    file = "aether-1.7.10-v1.1.2.3.jar",
    sha1 = "d45466de894c24fb2c7895704509073ea97eaf69",
    modrinth = @ModrinthFile(project = "YhmgMVyu", version = "4NuI8eHN"),
    curseforge = @CurseForgeFile(project = 255308, slug = "aether-legacy", file = 5028330),
    include = { "assets/aether_legacy/textures/**", "assets/aether_legacy/sounds/**" })
```

Fields: `name`, `file`, `sha1`, `modId` and `versions` (match any jar with that mod id and version), `alternativeOf`,
`curseforge`, `modrinth`, `urls` (extra mirrors), `include` and `exclude` (globs, `assets/**` by default), `remap`
(`from/ -> to/`), `neutralize` (default true), `side` (`CLIENT` or `BOTH`), `optional`. In properties, lists take
commas or semicolons, `curseforge` is `project:slug:file` and `modrinth` is `project:version`. Both ways are checked
at build time.

`DepEnsurerAPI` gives mods the jar and its entries at runtime.

## Dependencies

None.

## Building

`./gradlew build`. It builds both jars: `+depensurer-<version>.jar` and the CurseForge one,
`+depensurer-<version>-curseforge.jar`, which has no downloader. `verifyCurseForgeJar` checks that.

## Credits

* [GT:NH buildscript](https://github.com/GTNewHorizons/ExampleMod1.7.10)

## License

`LGPLv3`.

## Buy me some creatine

* [ko-fi.com](https://ko-fi.com/jackisasubtlejoke)
* Monero: `893tQ56jWt7czBsqAGPq8J5BDnYVCg2tvKpvwTcMY1LS79iDabopdxoUzNLEZtRTH4ewAcKLJ4DM4V41fvrJGHgeKArxwmJ`

<br>

![license](images/license_small.png)

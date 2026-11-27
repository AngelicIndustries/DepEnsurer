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

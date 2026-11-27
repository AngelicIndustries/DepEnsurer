import com.gtnewhorizons.gtnhgradle.GTNHGradlePlugin
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar
import net.darkhax.curseforgegradle.CurseForgeGradlePlugin
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

// The @EnsureDep annotation processor ships only in the api jar, so the mod jar carries no compiler classes.
val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named("main").get()
// Sources in src/processor/java and src/processor/resources, the defaults for this name
val processorSourceSet = sourceSets.create("processor") {
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(processorSourceSet.compileJavaTaskName).configure {
    val mainCompile = tasks.named<JavaCompile>("compileJava").get()
    sourceCompatibility = mainCompile.sourceCompatibility
    targetCompatibility = mainCompile.targetCompatibility
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

// The full build's downloader, the only network code. The default jar has it; the CurseForge jar leaves it out, and
// the shared code only finds it by name (Fetchers).
val fullSourceSet = sourceSets.create("full") {
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(fullSourceSet.compileJavaTaskName).configure {
    val mainCompile = tasks.named<JavaCompile>("compileJava").get()
    sourceCompatibility = mainCompile.sourceCompatibility
    targetCompatibility = mainCompile.targetCompatibility
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

tasks.named<Jar>("jar").configure {
    from(fullSourceSet.output)
}

tasks.named<Jar>("apiJar").configure {
    from(processorSourceSet.output)
}

dependencies {
    "testImplementation"(processorSourceSet.output)
    "testImplementation"(fullSourceSet.output)
    // Dev runs get the downloader too
    "runtimeOnlyNonPublishable"(fullSourceSet.output)
}

// The CurseForge jar: the shared code without the downloader, with Flavor and Fetchers from src/curseforge/java
// (the CurseForge build reports itself as such and finds no downloader). verifyCurseForgeJar checks it in `check`.
val curseForgeSourceSet = sourceSets.create("curseforge") {
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}

tasks.named<JavaCompile>(curseForgeSourceSet.compileJavaTaskName).configure {
    val mainCompile = tasks.named<JavaCompile>("compileJava").get()
    sourceCompatibility = mainCompile.sourceCompatibility
    targetCompatibility = mainCompile.targetCompatibility
    options.encoding = "UTF-8"
    options.annotationProcessorPath = configurations.named("annotationProcessor").get()
}

val curseForgeMcmodInfo = tasks.register<Copy>("curseForgeMcmodInfo") {
    from(tasks.named("processResources")) {
        include("mcmod.info")
    }
    into(layout.buildDirectory.dir("generated/curseforgeResources"))
    filter { line: String ->
        if (line.contains("\"description\"")) {
            line.replace(
                Regex("\",\\s*$"),
                " The CurseForge build never downloads anything: it lists missing files with links to their pages.\",",
            )
        } else {
            line
        }
    }
}

val curseForgeJar = tasks.register<Jar>("curseForgeJar") {
    group = "build"
    description = "Builds the CurseForge dev jar: no downloader, no network code."
    archiveClassifier.set("curseforge-dev")
    from(mainSourceSet.output) {
        exclude(
            "mcmod.info",
            "industries/angelic/depensurer/Flavor.class",
            "industries/angelic/depensurer/Fetchers.class",
        )
    }
    // Tags.class, which the GTNH buildscript compiles apart from main
    sourceSets.findByName("injectedTags")?.let { from(it.output) }
    from(curseForgeSourceSet.output)
    from(curseForgeMcmodInfo)
    manifest {
        attributes(
            "FMLCorePlugin" to "industries.angelic.depensurer.core.DepEnsurerCore",
            "FMLCorePluginContainsFMLMod" to "true",
        )
    }
}

val reobfCurseForgeJar = tasks.named<ReobfuscatedJar>("reobfCurseForgeJar") {
    archiveClassifier.set("curseforge")
}

tasks.named("assemble").configure {
    dependsOn(reobfCurseForgeJar)
}

val verifyCurseForgeJar = tasks.register("verifyCurseForgeJar") {
    group = "verification"
    description = "Fails if a CurseForge jar has the downloader, network code, or anything else that differs from the full jar."
    val curseDev = files(curseForgeJar)
    val curseObf = files(reobfCurseForgeJar)
    val fullDev = files(tasks.named("jar"))
    val fullObf = files(tasks.named("reobfJar"))
    inputs.files(curseDev, curseObf, fullDev, fullObf)
    doLast {
        val forbidden = listOf(
            "java/net/URLConnection",
            "java/net/HttpURLConnection",
            "java/net/Socket",
            "javax/net/ssl",
            "forgecdn",
            "api.modrinth.com",
            "fetch/Downloader",
            "fetch.Downloader",
            "depensurer.offline",
            "depensurer.modrinthApi",
            "depensurer.curseForgeCdn",
            "allowDownloads",
        )
        val replaced = setOf(
            "industries/angelic/depensurer/Flavor.class",
            "industries/angelic/depensurer/Fetchers.class",
            "mcmod.info",
        )
        fun contents(jar: File): Map<String, ByteArray> = java.util.zip.ZipFile(jar).use { zip ->
            zip.entries().asSequence().filter { !it.isDirectory }
                .associate { it.name to zip.getInputStream(it).readBytes() }
        }
        listOf(curseDev.singleFile to fullDev.singleFile, curseObf.singleFile to fullObf.singleFile).forEach { (curse, full) ->
            val c = contents(curse)
            val f = contents(full)
            check(f.containsKey("industries/angelic/depensurer/fetch/Downloader.class")) {
                "${full.name} has no downloader, so this check proves nothing"
            }
            val expected = f.keys.filter { !it.startsWith("industries/angelic/depensurer/fetch/") }.toSet()
            check(c.keys == expected) {
                "${curse.name} differs from ${full.name} beyond the downloader: extra ${c.keys - expected}, missing ${expected - c.keys}"
            }
            replaced.forEach { name ->
                check(!c.getValue(name).contentEquals(f.getValue(name))) { "${curse.name} has the full build's $name" }
            }
            c.forEach { (name, bytes) ->
                if (!name.endsWith(".png")) {
                    val text = String(bytes, Charsets.ISO_8859_1)
                    forbidden.firstOrNull { text.contains(it) }?.let { marker ->
                        throw GradleException("${curse.name}: $name contains '$marker'")
                    }
                }
            }
            check(String(c.getValue("mcmod.info"), Charsets.UTF_8).contains("The CurseForge build never downloads anything")) {
                "${curse.name} has no CurseForge note in mcmod.info"
            }
        }
    }
}

tasks.named("check").configure {
    dependsOn(verifyCurseForgeJar)
}

// CurseForge gets the CurseForge jar, so the GTNH buildscript's own upload (of the full jar) is turned off, as in
// AngelLib. The 67minecraft script tags the upload's sides from modrinthEnvironment.
val configuredCurseForgeProjectId = providers.gradleProperty("curseForgeProjectId").orNull?.trim().orEmpty()
val configuredCurseForgeRelations = providers.gradleProperty("curseForgeRelations").orNull?.trim().orEmpty()
val configuredMinecraftVersion = providers.gradleProperty("minecraftVersion").orNull?.trim() ?: "1.7.10"
extensions.findByType<GTNHGradlePlugin.GTNHExtension>()
    ?.configuration
    ?.curseForgeProjectId = ""

if (configuredCurseForgeProjectId.isNotEmpty()) {
    plugins.apply(CurseForgeGradlePlugin::class.java)
    val changelogFile = file(System.getenv("CHANGELOG_FILE") ?: "CHANGELOG.md")
    val releaseVersion = providers.provider { project.version.toString() }
    val publishCurseforge = tasks.register<TaskPublishCurseForge>("publishCurseforge") {
        group = "publishing"
        description = "Publishes the CurseForge jar to CurseForge."
        dependsOn(reobfCurseForgeJar)
        apiToken = providers.environmentVariable("CURSEFORGE_TOKEN")
        disableVersionDetection()
        val artifact = upload(configuredCurseForgeProjectId, reobfCurseForgeJar.flatMap { it.archiveFile })
        if (changelogFile.exists()) {
            artifact.changelogType = "markdown"
            artifact.changelog = changelogFile
        }
        artifact.releaseType = releaseVersion.map { if (it.endsWith("-pre")) "beta" else "release" }
        artifact.addGameVersion(configuredMinecraftVersion, "Forge")
        artifact.addModLoader("Forge")
        configuredCurseForgeRelations.split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { relation ->
                val parts = relation.split(":", limit = 2)
                if (parts.size == 2) {
                    artifact.addRelation(parts[1], parts[0])
                }
            }
    }
    if (providers.environmentVariable("CURSEFORGE_TOKEN").orNull != null) {
        tasks.named("publish").configure {
            dependsOn(publishCurseforge)
        }
    }
}

// Test jars for dev and harness runs, never published: a mod declaring an asset source, and a stand-in source
// whose coremod and mod throw if FML ever loads them. ./gradlew testJars puts both in build/testmods, the test mod
// reobfuscated for real clients and as a dev jar in build/testmods/dev for runServer.
fun testSourceSet(name: String) = sourceSets.create(name) {
    compileClasspath = mainSourceSet.compileClasspath + mainSourceSet.output
    runtimeClasspath = output + compileClasspath
}.also { sourceSet ->
    tasks.named<JavaCompile>(sourceSet.compileJavaTaskName).configure {
        val mainCompile = tasks.named<JavaCompile>("compileJava").get()
        sourceCompatibility = mainCompile.sourceCompatibility
        targetCompatibility = mainCompile.targetCompatibility
        options.encoding = "UTF-8"
        options.annotationProcessorPath = configurations.named("annotationProcessor").get()
    }
}

val testModSourceSet = testSourceSet("testmod")
val testTargetSourceSet = testSourceSet("testtarget")

// Reproducible, so its sha1 only changes with its contents
val testTargetJar = tasks.register<Jar>("testTargetJar") {
    from(testTargetSourceSet.output)
    archiveFileName.set("depensurer-testtarget.jar")
    destinationDirectory.set(layout.buildDirectory.dir("testmods"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    manifest {
        attributes(
            "FMLCorePlugin" to "depensurer.testtarget.TestTargetCore",
            "FMLCorePluginContainsFMLMod" to "true",
        )
    }
}

// The test mod declares the target by sha1 too, so the full build can download it from a local server
val testTargetHashDir = layout.buildDirectory.dir("generated/testmodHash")
val generateTestTargetHash = tasks.register("generateTestTargetHash") {
    val jar = testTargetJar.flatMap { it.archiveFile }
    val out = testTargetHashDir
    inputs.file(jar)
    outputs.dir(out)
    doLast {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
            .digest(jar.get().asFile.readBytes())
        val sha1 = digest.joinToString("") { "%02x".format(it) }
        val file = out.get().file("depensurer/testmod/TestTargetHash.java").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package depensurer.testmod;\n\n/** Generated from depensurer-testtarget.jar. */\nfinal class TestTargetHash {\n\n" +
                "    static final String SHA1 = \"$sha1\";\n\n    private TestTargetHash() {}\n}\n",
        )
    }
}
testModSourceSet.java.srcDir(testTargetHashDir)

tasks.named<JavaCompile>(testModSourceSet.compileJavaTaskName).configure {
    dependsOn(generateTestTargetHash)
    options.annotationProcessorPath = files(processorSourceSet.output, mainSourceSet.output) +
        configurations.named("annotationProcessor").get()
}

val testModJar = tasks.register<Jar>("testModJar") {
    from(testModSourceSet.output)
    archiveFileName.set("depensurer-testmod.jar")
    destinationDirectory.set(layout.buildDirectory.dir("testmods/dev"))
}

val reobfTestModJar = tasks.named<ReobfuscatedJar>("reobfTestModJar") {
    archiveFileName.set("depensurer-testmod.jar")
    destinationDirectory.set(layout.buildDirectory.dir("testmods"))
}

tasks.register("testJars") {
    group = "verification"
    description = "Builds the test mod and test target jars into build/testmods."
    dependsOn(reobfTestModJar, testTargetJar)
}

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar

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

tasks.named<Jar>("apiJar").configure {
    from(processorSourceSet.output)
}

dependencies {
    "testImplementation"(processorSourceSet.output)
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

tasks.named<JavaCompile>(testModSourceSet.compileJavaTaskName).configure {
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

val testTargetJar = tasks.register<Jar>("testTargetJar") {
    from(testTargetSourceSet.output)
    archiveFileName.set("depensurer-testtarget.jar")
    destinationDirectory.set(layout.buildDirectory.dir("testmods"))
    manifest {
        attributes(
            "FMLCorePlugin" to "depensurer.testtarget.TestTargetCore",
            "FMLCorePluginContainsFMLMod" to "true",
        )
    }
}

tasks.register("testJars") {
    group = "verification"
    description = "Builds the test mod and test target jars into build/testmods."
    dependsOn(reobfTestModJar, testTargetJar)
}

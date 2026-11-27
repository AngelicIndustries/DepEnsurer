// Dep Ensurer: declares a mod's asset sources from depensurer.* properties in gradle.properties, instead of with
// @EnsureDep. Writes META-INF/depensurer.json into the mod's resources, with the same checks the annotation processor
// makes. Apply it from build.gradle.kts after the GTNH plugin:
//   apply(from = "https://raw.githubusercontent.com/AngelicIndustries/DepEnsurer/<version>/depensurer.gradle.kts")
// The checking tool is Dep Ensurer's api jar from the Angelic Maven (depensurer.version, else this script's version),
// or a local build with -PdepEnsurerDir=<checkout>. The README lists the properties.

import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.WriteProperties

val depEnsurerVersion = providers.gradleProperty("depensurer.version").orElse("0.0.1").get()
val depEnsurerTool = configurations.create("depEnsurerTool") {
    isCanBeConsumed = false
    isTransitive = false
    // The mod's own runtime attributes (Java runtime, RetroFuturaGradle's mcp names), so Gradle can pick one of
    // Dep Ensurer's published variants; the :api classifier then picks the jar
    val runtime = configurations.getByName("runtimeClasspath").attributes
    attributes {
        runtime.keySet().forEach { key ->
            @Suppress("UNCHECKED_CAST")
            attribute(key as Attribute<Any>, runtime.getAttribute(key)!!)
        }
    }
}
val localDepEnsurer = providers.gradleProperty("depEnsurerDir").orNull
if (localDepEnsurer != null) {
    dependencies.add(
        depEnsurerTool.name,
        files("$localDepEnsurer/build/libs/+depensurer-$depEnsurerVersion-api.jar"),
    )
} else {
    dependencies.add(depEnsurerTool.name, "industries.angelic.depensurer:DepEnsurer:$depEnsurerVersion:api")
}

val mainSourceSet = project.extensions.getByType<SourceSetContainer>().named("main")
val mainClasses = mainSourceSet.get().output.classesDirs
val depEnsurerInput = layout.buildDirectory.file("depensurer/declaration.properties")
val depEnsurerOutput = layout.buildDirectory.dir("generated/depensurer")

val depEnsurerProperties = tasks.register<WriteProperties>("depEnsurerProperties") {
    destinationFile.set(depEnsurerInput)
    property("modId", providers.gradleProperty("modId").orElse("").get())
    providers.gradlePropertiesPrefixedBy("depensurer.").get().forEach { (key, value) -> property(key, value) }
}

val generateDepEnsurerDeclaration = tasks.register<JavaExec>("generateDepEnsurerDeclaration") {
    group = "build"
    description = "Writes META-INF/depensurer.json from the depensurer.* properties."
    dependsOn(depEnsurerProperties)
    classpath = depEnsurerTool
    mainClass.set("industries.angelic.depensurer.api.processor.PropertiesDeclaration")
    val input = depEnsurerInput
    val output = depEnsurerOutput
    inputs.file(input)
    outputs.dir(output)
    argumentProviders.add(
        CommandLineArgumentProvider { listOf(input.get().asFile.absolutePath, output.get().asFile.absolutePath) },
    )
    // The annotation processor writes the same file into the classes; a mod declares its deps one way or the other
    val classes = mainClasses
    mustRunAfter(tasks.named("compileJava"))
    doFirst {
        if (classes.any { File(it, "META-INF/depensurer.json").exists() }) {
            throw GradleException(
                "Dep Ensurer: this mod declares deps with @EnsureDep and in gradle.properties; use one or the other",
            )
        }
    }
}

mainSourceSet.configure {
    resources.srcDir(generateDepEnsurerDeclaration)
}

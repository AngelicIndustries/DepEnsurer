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

tasks.named<Jar>("apiJar").configure {
    from(processorSourceSet.output)
}

dependencies {
    "testImplementation"(processorSourceSet.output)
}

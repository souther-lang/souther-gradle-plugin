plugins {
    `java-gradle-plugin`
}

group = "org.souther-lang"
version = "0.1.0-SNAPSHOT"

// The Souther this plugin release is verified against: what a project that names no version gets,
// and what the tests load. One property, so a default nothing was tested against cannot happen.
val southerDefaultVersion = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
    // Where a Souther built from source lands when its build is not using ~/.m2 — a git worktree
    // keeps its own. Pass -PsoutherRepo=<path> to read from one.
    providers.gradleProperty("southerRepo").orNull?.let { maven { url = uri(it) } }
}

dependencies {
    implementation("org.souther-lang:souther-build-api:1.0.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Not a toolchain: this has to start on whatever JDK the build is already running so that a Souther
// needing a newer one is something it can report. 17 is what current Gradle runs on.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

// So the Souther version this release was verified against is written into the artifact rather than
// named in the code twice.
tasks.processResources {
    // Read into a local first: a closure that reaches out to the script itself cannot be stored in
    // the configuration cache, and this plugin has no business breaking it in its own build.
    val version = southerDefaultVersion
    filesMatching("souther-gradle-plugin.properties") {
        expand("southerVersion" to version)
    }
}

gradlePlugin {
    plugins {
        create("souther") {
            id = "org.souther-lang.souther"
            implementationClass = "souther.gradle.SoutherPlugin"
            displayName = "Souther"
            description = "Compiles Souther sources in a Gradle build."
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The tests build a real project against a real Souther. What version, and where it is, said
    // once here rather than in each generated build script.
    systemProperty("souther.default.version", southerDefaultVersion)
    providers.gradleProperty("southerRepo").orNull?.let { systemProperty("souther.repo", it) }
}

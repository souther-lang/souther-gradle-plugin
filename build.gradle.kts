plugins {
    `java-gradle-plugin`
    // For `./gradlew publishPlugins`, which is how a Gradle plugin reaches the projects that
    // declare it by id.
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "org.souther-lang"
version = "0.2.0"

// The Souther the tests compile with. It reaches no artifact — a project names the Souther it
// compiles with, and this plugin names none — so moving it is a decision about these tests.
val southerTestedVersion = "0.2.0"

repositories {
    mavenCentral()
    mavenLocal()
    // Where a Souther built from source lands when its build is not using ~/.m2 — a git worktree
    // keeps its own. Pass -PsoutherRepo=<path> to read from one.
    providers.gradleProperty("southerRepo").orNull?.let { maven { url = uri(it) } }
}

dependencies {
    implementation("org.souther-lang:souther-build-api:1.1.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Not a toolchain: this has to start on whatever JDK the build is already running so that a Souther
// needing a newer one is something it can report. 17 is what current Gradle runs on.
tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

// The Souther the test projects name, handed over rather than written in each of them. Read into a
// local first: a closure that reaches out to the script itself cannot be stored in the configuration
// cache, and this plugin has no business breaking it in its own build.
tasks.test {
    val version = southerTestedVersion
    systemProperty("souther.tested.version", version)
    // The version reaches a build script the test writes, so a change to it changes what the tests
    // do. Declared, or Gradle answers `test` up to date after it moves.
    inputs.property("southerTestedVersion", version)
}

gradlePlugin {
    website = "https://github.com/souther-lang/souther-gradle-plugin"
    vcsUrl = "https://github.com/souther-lang/souther-gradle-plugin.git"
    plugins {
        create("souther") {
            id = "org.souther-lang.souther"
            implementationClass = "souther.gradle.SoutherPlugin"
            displayName = "Souther"
            description = "Compiles Souther sources in a Gradle build. The compiler is not " +
                    "linked: the Souther version the project names is resolved and run in " +
                    "isolation, so this plugin and the Souther it runs are released separately."
            tags = listOf("souther", "jvm", "domain-model", "code-generation")
        }
    }
}

tasks.test {
    useJUnitPlatform()
    // The tests build a real project against a real Souther, and the version they get is the one
    // written into the artifact above — the generated build scripts name none, which is the whole
    // point of them. What is left to say is where that Souther is.
    providers.gradleProperty("southerRepo").orNull?.let { systemProperty("souther.repo", it) }
}

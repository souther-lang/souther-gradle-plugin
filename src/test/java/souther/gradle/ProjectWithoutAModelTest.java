package souther.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A project that applies the plugin and has no model. Applied across a build from a convention
 * plugin, this is most of them, and one of them should ask for no Souther at all.
 */
class ProjectWithoutAModelTest {

    /**
     * The one repository holds no Souther, so whatever the plugin asks for cannot resolve. That is
     * what makes this a test: compileSouther is skipped and resolves no toolchain, and the runtime
     * has to follow the same rule. Demanded anyway, it fails compileJava — in a project with
     * nothing Souther in it, over a model it does not have.
     */
    @Test
    void aProjectWithNoSouAsksForNoSouther(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.gradle.kts"), """
                rootProject.name = "no-model"
                """);
        Files.createDirectories(dir.resolve("empty-repository"));
        Files.writeString(dir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("org.souther-lang.souther")
                }

                repositories {
                    maven { url = uri("empty-repository") }
                }
                """);
        Path java = Files.createDirectories(dir.resolve("src/main/java/app"));
        Files.writeString(java.resolve("Hello.java"), """
                package app;

                public final class Hello {}
                """);

        BuildResult built = GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments("compileJava")
                .build();

        assertNotNull(built.task(":compileSouther"), "the task is registered either way");
        assertEquals(TaskOutcome.NO_SOURCE, built.task(":compileSouther").getOutcome());
        assertEquals(TaskOutcome.SUCCESS, built.task(":compileJava").getOutcome(),
                "the Java of a project with no model compiles without a Souther to resolve");
    }
}

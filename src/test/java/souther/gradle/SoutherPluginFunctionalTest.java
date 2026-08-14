package souther.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A real Gradle build over a project whose sources are only {@code .sou}, wired with nothing but the
 * plugin declaration. This is what the issue this plugin exists for asks for.
 */
class SoutherPluginFunctionalTest {

    @Test
    void aProjectWhoseSourcesAreOnlySouGetsItsClassesIntoItsJar(@TempDir Path dir)
            throws IOException {
        project(dir);

        BuildResult built = gradle(dir, "jar").build();

        assertTrue(built.getOutput().contains("BUILD SUCCESSFUL"), built.getOutput());
        try (ZipFile jar = new ZipFile(dir.resolve("build/libs/sou-only.jar").toFile())) {
            assertNotNull(jar.getEntry("shared/money/Amount.class"),
                    "the model is in the jar, and no file in another language was written to get "
                            + "it there");
            assertNotNull(jar.getEntry("shared/money/$Module.class"),
                    "the declarations another project imports this module by");
        }
    }

    /**
     * The failure this plugin's own issue reports twice over: an arrangement that did not declare
     * the sources as an input left the task up to date, and a build after an edit produced the
     * previously generated classes while succeeding.
     */
    @Test
    void editingASouAndBuildingAgainProducesTheEditedModel(@TempDir Path dir) throws IOException {
        project(dir);
        gradle(dir, "jar").build();

        Files.writeString(dir.resolve("src/main/souther/money.sou"), """
                module shared.money exposing ( Amount, Purse )

                data Amount = Int
                    invariant value >= 0

                data Purse = { held: Amount }
                """);
        BuildResult again = gradle(dir, "jar").build();

        assertEquals(TaskOutcome.SUCCESS, again.task(":compileSouther").getOutcome(),
                "an edited source is not up to date");
        try (ZipFile jar = new ZipFile(dir.resolve("build/libs/sou-only.jar").toFile())) {
            assertNotNull(jar.getEntry("shared/money/Purse.class"), "the edited model, not the old one");
        }
    }

    /**
     * Java beside the model, referring to it. The annotation processor this replaces gave a project
     * that for nothing — the generated types were emitted into the same javac run — so losing it
     * would be a step back, and the project should not have to name the runtime the generated code
     * calls either.
     */
    @Test
    void javaBesideTheModelCompilesAgainstItWithoutTheProjectNamingAnything(@TempDir Path dir)
            throws IOException {
        project(dir);
        Path java = Files.createDirectories(dir.resolve("src/main/java/app"));
        Files.writeString(java.resolve("Purses.java"), """
                package app;

                import shared.money.Amount;
                import souther.runtime.InvariantFailure;
                import souther.runtime.Result;

                public final class Purses {
                    public static Result<Amount, InvariantFailure> of(long n) {
                        return Amount.__construct(n);
                    }
                }
                """);

        BuildResult built = gradle(dir, "jar").build();

        assertTrue(built.getOutput().contains("BUILD SUCCESSFUL"), built.getOutput());
        try (ZipFile jar = new ZipFile(dir.resolve("build/libs/sou-only.jar").toFile())) {
            assertNotNull(jar.getEntry("app/Purses.class"));
            assertNotNull(jar.getEntry("shared/money/Amount.class"));
        }
    }

    /**
     * The configuration cache is the reason a plugin gets released without Souther moving, so this
     * plugin had better not be the thing that stops a project using it. Twice, because storing an
     * entry and reusing one fail differently.
     */
    @Test
    void theBuildWorksWithTheConfigurationCacheAndReusesItsEntry(@TempDir Path dir)
            throws IOException {
        project(dir);

        gradle(dir, "jar", "--configuration-cache").build();
        BuildResult again = gradle(dir, "jar", "--configuration-cache").build();

        assertTrue(again.getOutput().contains("Reusing configuration cache"), again.getOutput());
    }

    /** Nothing edited is nothing to do, or every build recompiles every model in the project. */
    @Test
    void anUneditedProjectIsUpToDate(@TempDir Path dir) throws IOException {
        project(dir);
        gradle(dir, "jar").build();

        BuildResult again = gradle(dir, "jar").build();

        assertEquals(TaskOutcome.UP_TO_DATE, again.task(":compileSouther").getOutcome());
    }

    /** A project with only a plugin declaration and a {@code .sou}. */
    private static void project(Path dir) throws IOException {
        Files.writeString(dir.resolve("settings.gradle.kts"), """
                rootProject.name = "sou-only"
                """);
        Files.writeString(dir.resolve("build.gradle.kts"), """
                plugins {
                    java
                    id("org.souther-lang.souther")
                }

                repositories {
                    mavenCentral()
                    mavenLocal()
                    maven { url = uri("%s") }
                }
                """.formatted(southerRepository()));
        Path sources = Files.createDirectories(dir.resolve("src/main/souther"));
        Files.writeString(sources.resolve("money.sou"), """
                module shared.money exposing ( Amount )

                data Amount = Int
                    invariant value >= 0
                """);
    }

    private static GradleRunner gradle(Path dir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    /** Where a Souther built from source is, handed in by this project's own build. */
    private static String southerRepository() {
        String repository = System.getProperty("souther.repo");
        if (repository == null) {
            throw new IllegalStateException(
                    "-PsoutherRepo=<path to a repository holding souther-build-driver> is what "
                            + "these tests compile against");
        }
        return repository;
    }
}

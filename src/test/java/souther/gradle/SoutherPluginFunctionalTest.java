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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * Renaming a module takes the old name out of the jar, {@code $Module} included — that is what
     * another project imports it by, so leaving it would let a build downstream go on importing a
     * module that is no longer written anywhere. A task's output directory is not emptied between
     * runs, so this is the compile's to take back.
     */
    @Test
    void aRenamedModuleLeavesNothingOfTheOldNameInTheJar(@TempDir Path dir) throws IOException {
        project(dir);
        gradle(dir, "jar").build();

        Files.writeString(dir.resolve("src/main/souther/money.sou"), """
                module shared.wallet exposing ( Amount )

                data Amount = Int
                    invariant value >= 0
                """);
        gradle(dir, "jar").build();

        try (ZipFile jar = new ZipFile(dir.resolve("build/libs/sou-only.jar").toFile())) {
            assertNotNull(jar.getEntry("shared/wallet/$Module.class"));
            assertNull(jar.getEntry("shared/money/$Module.class"));
            assertNull(jar.getEntry("shared/money/Amount.class"));
        }
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
                    %s
                }
                """.formatted(extraRepository()));
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

    /**
     * A repository to read Souther from besides the released one, for a Souther built from source.
     * Empty unless -PsoutherRepo=<path> is passed, so the default run resolves the released Souther
     * the same way a project that applies this plugin does.
     */
    private static String extraRepository() {
        String repository = System.getProperty("souther.repo");
        return repository == null ? "" : "maven { url = uri(\"" + repository + "\") }";
    }
}

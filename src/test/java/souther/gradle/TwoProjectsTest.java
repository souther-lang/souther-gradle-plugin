package souther.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What one project's model is worth to another. Cross-project import is what a Souther library is
 * published for (ADR-0063), and a project that depends on such a library gets nothing from it if it
 * cannot compile against what it exposes.
 */
class TwoProjectsTest {

    @Test
    void aModuleCompiledByOneProjectIsImportedByAnother(@TempDir Path dir) throws IOException {
        twoProjects(dir, "java", """
                plugins { java; id("org.souther-lang.souther") }
                dependencies { implementation(project(":money")) }
                """);
        Files.writeString(dir.resolve("orders/src/main/souther/orders.sou"), """
                module app.orders exposing ( Order )

                import shared.money ( Amount )

                data Order = { total: Amount }
                """);

        BuildResult built = gradle(dir, ":orders:jar").build();

        assertTrue(built.getOutput().contains("BUILD SUCCESSFUL"), built.getOutput());
        try (ZipFile jar = new ZipFile(dir.resolve("orders/build/libs/orders.jar").toFile())) {
            assertNotNull(jar.getEntry("app/orders/Order.class"));
            assertNull(jar.getEntry("shared/money/Amount.class"),
                    "the dependency's classes belong to its own build");
        }
    }

    /**
     * A library exposing generated types exposes the runtime with them: a consumer naming
     * {@code Result} has to have it to compile. That makes it part of the library's API, not
     * something behind it.
     */
    @Test
    void aConsumerOfALibrarysModelCanNameWhatThatModelReturns(@TempDir Path dir) throws IOException {
        // No Souther of its own, and no plugin: this is a project that only consumes a library.
        // Applying the plugin here would add the runtime and hide whether the library exposes it.
        twoProjects(dir, "java-library", """
                plugins { java }
                dependencies { implementation(project(":money")) }
                """);
        Path java = Files.createDirectories(dir.resolve("orders/src/main/java/app"));
        Files.writeString(java.resolve("Orders.java"), """
                package app;

                import shared.money.Amount;
                import souther.runtime.InvariantFailure;
                import souther.runtime.Result;

                public final class Orders {
                    private Orders() {}

                    public static Result<Amount, InvariantFailure> of(long n) {
                        return Amount.__construct(n);
                    }
                }
                """);

        BuildResult built = gradle(dir, ":orders:jar").build();

        assertTrue(built.getOutput().contains("BUILD SUCCESSFUL"), built.getOutput());
    }

    /**
     * A build of two projects: {@code money} holds the model, {@code orders} depends on it.
     *
     * @param modelPlugins what the model project applies beside this one
     * @param ordersScript the whole of the depending project's build script
     */
    private static void twoProjects(Path dir, String modelPlugins, String ordersScript)
            throws IOException {
        Files.writeString(dir.resolve("settings.gradle.kts"), """
                rootProject.name = "two"
                include("money", "orders")
                """);
        Files.writeString(dir.resolve("build.gradle.kts"), """
                subprojects {
                    repositories {
                        mavenCentral()
                        mavenLocal()
                        maven { url = uri("%s") }
                    }
                }
                """.formatted(southerRepository()));

        Files.createDirectories(dir.resolve("money/src/main/souther"));
        Files.writeString(dir.resolve("money/build.gradle.kts"), """
                plugins { `%s`; id("org.souther-lang.souther") }
                """.formatted(modelPlugins));
        Files.writeString(dir.resolve("money/src/main/souther/money.sou"), """
                module shared.money exposing ( Amount )

                data Amount = Int
                    invariant value >= 0
                """);

        Files.createDirectories(dir.resolve("orders/src/main/souther"));
        Files.writeString(dir.resolve("orders/build.gradle.kts"), ordersScript);
    }

    private static GradleRunner gradle(Path dir, String... arguments) {
        return GradleRunner.create()
                .withProjectDir(dir.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private static String southerRepository() {
        String repository = System.getProperty("souther.repo");
        if (repository == null) {
            throw new IllegalStateException("-PsoutherRepo=<path> is what these tests build against");
        }
        return repository;
    }
}

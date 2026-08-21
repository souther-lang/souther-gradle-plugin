package souther.gradle;

import souther.build.BuildDiagnostic;
import souther.build.BuildRequest;
import souther.build.BuildResult;
import souther.build.DriverLoader;
import souther.build.SoutherBuildDriver;

import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The compile, inside the worker.
 *
 * <p>Here rather than in the task because this is where the Souther the project asked for is: the
 * worker runs under a loader Gradle composed from this plugin's classes and the resolved toolchain,
 * so the driver is reachable and the compiler behind it never meets Gradle's own class path.
 *
 * <p>A work action has no return value, so what the compile had to say is reported from here and a
 * failure is raised from here.
 */
public abstract class SoutherCompileWork implements WorkAction<SoutherCompileWork.Parameters> {

    /** Gradle-managed types, which is what may cross into a worker. The request is built here. */
    public interface Parameters extends WorkParameters {
        DirectoryProperty getSourceDirectory();

        ConfigurableFileCollection getClassPath();

        DirectoryProperty getOutputDirectory();

        DirectoryProperty getStateDirectory();

        Property<String> getLanguage();
    }

    private static final Logger LOG = Logging.getLogger(SoutherCompileWork.class);

    @Override
    public void execute() {
        SoutherBuildDriver driver = DriverLoader.foundIn(getClass().getClassLoader());
        List<Path> classPath = new ArrayList<>();
        for (File entry : getParameters().getClassPath()) {
            classPath.add(entry.toPath());
        }
        BuildResult result = driver.compile(new BuildRequest(
                List.of(getParameters().getSourceDirectory().get().getAsFile().toPath()),
                classPath,
                getParameters().getOutputDirectory().get().getAsFile().toPath(),
                getParameters().getStateDirectory().get().getAsFile().toPath(),
                getParameters().getLanguage().getOrNull()));
        report(result);
    }

    /**
     * What stops the build says how many rather than what: each diagnostic is already in the log
     * with its snippet, and naming one of them here would put a second copy of that one under all
     * of them.
     */
    private static void report(BuildResult result) {
        int errors = 0;
        for (BuildDiagnostic diagnostic : result.diagnostics()) {
            if (diagnostic.severity() == BuildDiagnostic.Severity.ERROR) {
                errors++;
                LOG.error(diagnostic.rendered());
            } else {
                LOG.warn(diagnostic.rendered());
            }
        }
        if (!result.succeeded()) {
            throw new GradleException(switch (errors) {
                // A failure that named no error of its own. Saying "0 errors" would read as nothing
                // having gone wrong.
                case 0 -> "Souther failed without reporting an error.";
                case 1 -> "Souther reported 1 error.";
                default -> "Souther reported " + errors + " errors.";
            });
        }
    }
}

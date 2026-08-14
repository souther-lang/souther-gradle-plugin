package souther.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;

import javax.inject.Inject;
import java.io.File;

/** Compiles this project's Souther sources. */
public abstract class SoutherCompile extends DefaultTask {

    /**
     * The {@code .sou} themselves, so that editing one is not up to date — the arrangement this
     * plugin replaces had to be told that by hand, and a build that was not told produced the
     * previously generated classes.
     *
     * <p>Skipped when empty, which is how a project with no Souther in it is left alone.
     */
    @InputFiles
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSourceFiles();

    /** The root the sources are under, which is what the compiler is pointed at. */
    @Internal
    public abstract DirectoryProperty getSourceDirectory();

    /** The Souther that runs the compile: souther-build-driver and everything behind it. */
    @Classpath
    public abstract ConfigurableFileCollection getToolchain();

    /** What an import of another project's module resolves against. */
    @Classpath
    public abstract ConfigurableFileCollection getCompileClasspath();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /**
     * What the compile keeps between runs: the record of what it generated, which is how a class it
     * no longer generates is taken back out. An output of this task, so a clean takes it too.
     */
    @OutputDirectory
    public abstract DirectoryProperty getStateDirectory();

    @Input
    @Optional
    public abstract Property<String> getLanguage();

    @Inject
    protected abstract WorkerExecutor getWorkerExecutor();

    @TaskAction
    void compile() {
        // Class-loader isolation, so the compiler runs against the libraries the Souther release
        // was built with rather than the ones Gradle and this plugin happen to carry.
        WorkQueue queue = getWorkerExecutor()
                .classLoaderIsolation(spec -> spec.getClasspath().from(getToolchain()));
        // Everything on the class path except where this compile writes. That directory is on the
        // source set's compile class path so the rest of the project can name the model, and after
        // one build it holds the very modules being compiled — read as a dependency they are the
        // same module arriving twice, which is refused.
        File output = getOutputDirectory().get().getAsFile().getAbsoluteFile();
        FileCollection dependencies =
                getCompileClasspath().filter(entry -> !entry.getAbsoluteFile().equals(output));
        queue.submit(SoutherCompileWork.class, parameters -> {
            parameters.getSourceDirectory().set(getSourceDirectory());
            parameters.getClassPath().from(dependencies);
            parameters.getOutputDirectory().set(getOutputDirectory());
            parameters.getStateDirectory().set(getStateDirectory());
            parameters.getLanguage().set(getLanguage());
        });
    }
}

package souther.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;


/**
 * Everything a project needs to compile Souther, so that its build script says only that it does.
 *
 * <p>The compiler is not on this plugin's class path. What the project names is a Souther version,
 * and that is resolved as one artifact — souther-build-driver — whose own dependencies are what a
 * release of that Souther needs.
 */
public class SoutherPlugin implements Plugin<Project> {

    /** The configuration the toolchain is resolved through. */
    public static final String TOOLCHAIN = "southerToolchain";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(JavaPlugin.class);
        SoutherExtension souther = project.getExtensions().create("souther", SoutherExtension.class);
        souther.getSoutherVersion().convention(SoutherRelease.verified());
        souther.getSourceDirectory().convention(
                project.getLayout().getProjectDirectory().dir("src/main/souther"));

        Configuration toolchain = project.getConfigurations().create(TOOLCHAIN, it -> {
            it.setCanBeConsumed(false);
            it.setCanBeResolved(true);
            it.setDescription("The Souther that compiles this project's .sou.");
            // As a default dependency, so the version is read when the configuration is resolved
            // rather than while the build script is still being written.
            it.defaultDependencies(dependencies -> dependencies.add(project.getDependencies()
                    .create("org.souther-lang:souther-build-driver:"
                            + souther.getSoutherVersion().get())));
        });

        SourceSet main = project.getExtensions().getByType(SourceSetContainer.class)
                .getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        // Held before the generated classes are added to it below. A task is configured lazily, so
        // reading the source set's class path from inside the block below would read the one this
        // task contributes to, and the task would depend on itself.
        FileCollection dependencies = main.getCompileClasspath();
        TaskProvider<SoutherCompile> compile = project.getTasks()
                .register("compileSouther", SoutherCompile.class, task -> {
                    task.setDescription("Compiles the Souther sources of the main source set.");
                    task.setGroup("build");
                    task.getSourceDirectory().set(souther.getSourceDirectory());
                    task.getSourceFiles().from(project.fileTree(souther.getSourceDirectory(),
                            tree -> tree.include("**/*.sou")));
                    task.getToolchain().from(toolchain);
                    task.getCompileClasspath().from(dependencies);
                    task.getLanguage().set(souther.getLanguage());
                    task.getOutputDirectory().set(
                            project.getLayout().getBuildDirectory().dir("classes/souther/main"));
                    task.getStateDirectory().set(
                            project.getLayout().getBuildDirectory().dir("tmp/compileSouther"));
                });

        // Among the source set's class directories, which is what puts the generated classes into
        // the jar and onto the test compile class path without a project having to arrange either.
        // Class directories rather than the output's plain directories: a project depending on this
        // one is offered the classes rather than the jar, and that offer is made out of these. Added
        // as an output directory only, a library's own consumers could not see its model.
        ((ConfigurableFileCollection) main.getOutput().getClassesDirs())
                .from(compile.flatMap(SoutherCompile::getOutputDirectory));

        // And on the compile class path of the source set they belong to, so Java or Kotlin written
        // beside the model can name it. The annotation processor gave a project that for nothing —
        // the types were emitted into the same javac run — and losing it would be a step back.
        // SoutherCompile leaves this directory out of what it hands the compiler, or the modules it
        // is compiling would arrive as a dependency of themselves.
        main.setCompileClasspath(main.getCompileClasspath()
                .plus(project.files(compile.flatMap(SoutherCompile::getOutputDirectory))));

        // The generated code calls the runtime, so a project depends on it whether or not it says
        // so. Which version is not something a project should have to know: it is the one belonging
        // to the Souther that compiled the model.
        Provider<String> runtime = souther.getSoutherVersion()
                .map(it -> "org.souther-lang:souther-runtime:" + it);
        project.getDependencies().addProvider(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, runtime);
        // And on the API of a library, because a generated type names the runtime in its own
        // signatures: a project consuming this one cannot say what a behavior returns without it.
        // Also on implementation above, which java-library extends from it — one artifact, said
        // where a consumer can see it.
        project.getPluginManager().withPlugin("java-library", applied ->
                project.getDependencies().addProvider(JavaPlugin.API_CONFIGURATION_NAME, runtime));
    }
}

package souther.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/** What a project says about Souther. */
public abstract class SoutherExtension {

    /**
     * The Souther to compile with, which a project that has a model names. There is no default: a
     * version here would be one this plugin's release chose, and it would name an older Souther the
     * day after the next one came out. {@code latest.release} is a version like any other, for a
     * build that would rather have whatever is newest than say which.
     */
    public abstract Property<String> getSoutherVersion();

    /** Where the {@code .sou} are. */
    public abstract DirectoryProperty getSourceDirectory();

    /** The language diagnostics are written in. Unset is what a command line naming none gets. */
    public abstract Property<String> getLanguage();
}

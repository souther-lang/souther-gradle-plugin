package souther.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

/** What a project says about Souther. */
public abstract class SoutherExtension {

    /**
     * The Souther to compile with. Unset is the one this plugin release was verified against, so a
     * project that is happy with that names no version at all — and one that wants a newer Souther
     * says so without waiting for a plugin release.
     */
    public abstract Property<String> getSoutherVersion();

    /** Where the {@code .sou} are. */
    public abstract DirectoryProperty getSourceDirectory();

    /** The language diagnostics are written in. Unset is what a command line naming none gets. */
    public abstract Property<String> getLanguage();
}

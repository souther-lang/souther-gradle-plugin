package souther.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Properties;

/** The Souther this plugin release was verified against. */
final class SoutherRelease {

    private SoutherRelease() {}

    private static final String RESOURCE = "/souther-gradle-plugin.properties";

    /**
     * The version a project gets when it names none.
     *
     * <p>Written in at build time rather than named in the code, so that what this plugin was tested
     * against and what it defaults to are one thing. It is not this plugin's own version: the two
     * move for different reasons.
     */
    static String verified() {
        Properties properties = new Properties();
        try (InputStream in = SoutherRelease.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("this plugin was built without " + RESOURCE);
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("unreadable " + RESOURCE, e);
        }
        String version = properties.getProperty("souther.version");
        if (version == null || version.isBlank()) {
            // Not a default: an empty version reaches resolution as
            // org.souther-lang:souther-build-driver: and fails somewhere that says nothing about
            // where it came from.
            throw new IllegalStateException(RESOURCE + " names no souther.version");
        }
        return version;
    }
}

package souther.gradle;

/**
 * The Souther the test projects compile with.
 *
 * <p>Handed over by this plugin's own build rather than written here. A test naming a version of its
 * own would be a second answer to which Souther these projects use, and the day the build moved its
 * the two would differ without anything saying so.
 */
final class TestedSouther {

    private TestedSouther() {}

    /** What the build stated, refusing a run that was not told. */
    static String version() {
        String version = System.getProperty("souther.tested.version");
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("this test was run without souther.tested.version, "
                    + "which this plugin's build states and hands over. Run the tests through it.");
        }
        return version;
    }

    /** How a build script under test says it, since the plugin no longer says it for anyone. */
    static String block() {
        return """
                souther {
                    southerVersion = "%s"
                }
                """.formatted(version());
    }
}

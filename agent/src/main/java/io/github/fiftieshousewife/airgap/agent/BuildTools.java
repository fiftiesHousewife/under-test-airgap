package io.github.fiftieshousewife.airgap.agent;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Recognises the build tools' own classes, whose native libraries (Gradle's file watching, Maven's coloured console)
// are theirs, not the project's. See the decision "Native libraries are refused unless allowed, except the JDK's and
// the build tool's own". Applications use Maven's console library too, so it counts as the build tool's only in the
// build JVM.
final class BuildTools {

    private static final List<String> ALWAYS = Collections.unmodifiableList(Arrays.asList(
            "org.gradle.", "worker.org.gradle.", "net.rubygrapefruit.", "org.apache.maven."));
    private static final String MAVEN_CONSOLE_LIBRARY = "org.fusesource.jansi.";

    private BuildTools() {
    }

    // Whether a class belongs to the build tool.
    //
    // className: the class asking for a native library
    // run: the kind of JVM: test, application or build
    // Returns true for Gradle's and Maven's own classes
    static boolean own(final String className, final String run) {
        for (final String prefix : ALWAYS) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return "build".equals(run) && className.startsWith(MAVEN_CONSOLE_LIBRARY);
    }
}

package io.github.fiftieshousewife.airgap.maven;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

// Finds the agent's and the JUnit listener's jars, which come with the extension as its dependencies: on the class path
// Maven loaded it with, from .mvn/extensions.xml or -Dmaven.ext.class.path.
final class Jars {

    static final String AGENT_CLASS = "io/github/fiftieshousewife/airgap/agent/AirgapAgent.class";
    static final String LISTENER_CLASS = "io/github/fiftieshousewife/airgap/junit/AllowCalls.class";

    private Jars() {
    }

    // loader: the class loader the extension was loaded with
    // resource: a class file only that jar has
    // name: the jar's artifact, for the message if it's missing
    // Returns the jar
    // Throws IllegalStateException if the jar isn't there, or isn't a file
    static File find(final ClassLoader loader, final String resource, final String name) {
        final URL found = loader.getResource(resource);
        if (found == null || !"jar".equals(found.getProtocol())) {
            throw new IllegalStateException("airgap: " + name + " isn't on the extension's class path; name it with"
                    + " the extension in -Dmaven.ext.class.path, or load the extension from .mvn/extensions.xml");
        }
        return fileOf(found, name);
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "The path is the extension's own class loader's, naming a jar Maven loaded")
    private static File fileOf(final URL found, final String name) {
        final String file = found.getPath().substring(0, found.getPath().indexOf("!/"));
        try {
            return new File(new URI(file));
        } catch (URISyntaxException | IllegalArgumentException wrong) {
            throw new IllegalStateException("airgap: can't find the file of " + name + " (" + found + ")", wrong);
        }
    }
}

package example.airgap.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

// Reads the running JDK's own classes and finds every public method that can reach one of the JDK's last steps
// before a risky call without passing a hook. Each must be on the reviewed list, with why it is safe; anything else is
// a way round the agent. Every route found is written to the file named by airgap.routes.out.
class JdkRoutesTest {

    private static Routes routes;

    @BeforeAll
    static void readTheJdk() throws IOException {
        final CallGraph graph = new CallGraph();
        JdkClassFiles.forEach(graph::add);
        graph.link();
        routes = new Routes(graph);
    }

    @Test
    void findsEachKindsLastStepsAndTheHooks() {
        assertAll(
                () -> assertThat(Routes.LAST_STEPS.keySet()).allSatisfy(kind ->
                        assertThat(routes.lastStepsOf(kind)).as("last steps of " + kind).isNotEmpty()),
                () -> assertThat(routes.hooked()).as("hooked methods found in the JDK's classes").isNotEmpty());
    }

    @Test
    void everyPublicRouteToARiskyCallPassesAHookOrIsReviewed() throws IOException {
        final Map<String, String> reviewed = reviewed();
        final Map<String, String> unreviewed = new TreeMap<>();
        final List<String> all = new ArrayList<>();
        for (final String kind : Routes.LAST_STEPS.keySet()) {
            for (final Map.Entry<String, String> route : routes.unhooked(kind).entrySet()) {
                all.add(kind + "\t" + route.getKey() + "\t" + route.getValue());
                if (!reviewed.containsKey(route.getKey())) {
                    unreviewed.put(route.getKey(), kind + ": " + route.getValue());
                }
            }
        }
        writeOut(all);
        assertThat(unreviewed).as("public JDK methods that reach a risky call without passing a hook").isEmpty();
    }

    // Every route found, to the file airgap.routes.out names, if it names one
    private static void writeOut(final List<String> routesFound) throws IOException {
        final String out = System.getProperty("airgap.routes.out");
        if (out == null) {
            return;
        }
        final Path file = Paths.get(out).toAbsolutePath();
        final Path folder = file.getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        Files.write(file, routesFound, StandardCharsets.UTF_8);
    }

    // The reviewed list: one method per line, then # and why it is safe.
    private static Map<String, String> reviewed() {
        final Map<String, String> entries = new TreeMap<>();
        for (final String line : ResourceLines.read("reviewed-routes.txt")) {
            final int hash = line.indexOf('#');
            entries.put((hash < 0 ? line : line.substring(0, hash)).trim(),
                    hash < 0 ? "" : line.substring(hash + 1).trim());
        }
        return entries;
    }
}

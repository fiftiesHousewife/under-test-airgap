package example.airgap.examples;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Each example old service, run end to end under the agent, reaches exactly what its approved report summary says:
// the systems, how each call ended, and which tests and lines of the service made them.
class ExamplesTest {

    @TempDir
    Path folder;

    @Test
    void springWithGradleMatchesItsApprovedReport() throws IOException {
        final String made = ExampleRuns.gradle(ExampleRuns.copy("spring-orders", folder));
        assertThat(made).isEqualTo(ExampleRuns.approved("spring-orders", "gradle", made));
    }

    @Test
    void springWithMavenMatchesItsApprovedReport() throws IOException, InterruptedException {
        final String made = ExampleRuns.maven(ExampleRuns.copy("spring-orders", folder));
        assertThat(made).isEqualTo(ExampleRuns.approved("spring-orders", "maven", made));
    }

    @Test
    void plainJavaMatchesItsApprovedReport() throws IOException {
        final String made = ExampleRuns.gradle(ExampleRuns.copy("plain-billing", folder));
        assertThat(made).isEqualTo(ExampleRuns.approved("plain-billing", "gradle", made));
    }
}

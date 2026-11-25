package example.airgap.routes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// The lists the route check keeps beside its classes, as text: one entry a line, with blank lines and # comments left
// out.
final class ResourceLines {

    private ResourceLines() {
    }

    static List<String> read(final String name) {
        final InputStream list = ResourceLines.class.getResourceAsStream(name);
        if (list == null) {
            throw new UncheckedIOException(new IOException(name + " is missing from the test's resources"));
        }
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(list, StandardCharsets.UTF_8))) {
            return entries(lines);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> entries(final BufferedReader lines) throws IOException {
        final List<String> entries = new ArrayList<>();
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            if (!line.trim().isEmpty() && !line.startsWith("#")) {
                entries.add(line.trim());
            }
        }
        return entries;
    }
}

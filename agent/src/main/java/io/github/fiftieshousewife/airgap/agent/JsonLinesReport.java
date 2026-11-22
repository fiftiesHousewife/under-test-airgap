package io.github.fiftieshousewife.airgap.agent;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * The report file: one line of JSON per call. Several JVMs (forked test workers, say) may write the same file at
 * once, so each line is written whole under a file lock. Reporting never stops a refusal: if the file can't be
 * written, the call is still refused, and the failure is kept for {@link #lastFailure()}.
 */
public final class JsonLinesReport implements Report {

    private final Path file;
    private volatile IOException lastFailure;

    /**
     * A report written to a file.
     *
     * @param file the file; its folder is created when the first call is recorded
     */
    public JsonLinesReport(final Path file) {
        this.file = file.toAbsolutePath();
    }

    @Override
    public void record(final Call call) {
        final byte[] line = (call.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            final Path folder = file.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            write(line);
        } catch (IOException e) {
            lastFailure = e;
        }
    }

    private synchronized void write(final byte[] line) throws IOException {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.APPEND); FileLock lock = channel.lock()) {
            final ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining() && lock.isValid()) {
                channel.write(buffer);
            }
        }
    }

    /** @return the last failure to write the report, or {@code null} if every line was written */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The failure is handed over for reading only")
    public IOException getLastFailure() {
        return lastFailure;
    }
}

package io.github.fiftieshousewife.airgap.agent;

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
 * once, so each line is written whole under a file lock. No report must mean no risky calls, so the agent won't start
 * if it can't write the report's folder ({@link #checkWritable()}), and a line that can't be written is an error the
 * caller hears of.
 */
public final class JsonLinesReport implements Report {

    private final Path file;

    /**
     * A report written to a file.
     *
     * @param file the file; its folder is created when the first call is recorded
     */
    public JsonLinesReport(final Path file) {
        this.file = file.toAbsolutePath();
    }

    /**
     * Makes the report's folder, and checks the report can be written there, without writing it.
     *
     * @throws IllegalStateException if it can't, naming the folder
     */
    public void checkWritable() {
        final Path folder = file.getParent();
        try {
            if (folder != null) {
                Files.createDirectories(folder);
            }
        } catch (IOException e) {
            throw cantWrite(folder, e.toString());
        }
        if (folder != null && !Files.isWritable(folder) || Files.exists(file) && !Files.isWritable(file)) {
            throw cantWrite(folder, "not writable");
        }
    }

    private IllegalStateException cantWrite(final Path folder, final String why) {
        return new IllegalStateException("The airgap agent can't write its report " + file + " in " + folder
                + " (" + why + "), so it won't start: without its report, nobody would know what was refused.");
    }

    @Override
    public void record(final Call call) throws IOException {
        final byte[] line = (call.toJson() + "\n").getBytes(StandardCharsets.UTF_8);
        final Path folder = file.getParent();
        if (folder != null) {
            Files.createDirectories(folder);
        }
        // A file channel fails on an interrupted thread, and code that is shutting down interrupts its threads; the
        // flag is cleared for the write and put back after.
        final boolean interrupted = Thread.interrupted();
        try {
            write(line);
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
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
}

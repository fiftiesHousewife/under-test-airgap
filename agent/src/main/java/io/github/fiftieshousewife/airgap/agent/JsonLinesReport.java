package io.github.fiftieshousewife.airgap.agent;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * The report file: one line of JSON per call. Several JVMs (forked test workers, say) may write the same file at
 * once, so each line is written whole under a file lock. No report must mean no risky calls, so the agent won't start
 * if it can't write the report's folder ({@link #checkWritable()}), and a line that can't be written is an error the
 * caller hears of.
 *
 * <p>Each JVM also rebuilds the report's {@link Summary} from the whole report as it ends ({@link #summarise()}):
 * none knows which will end last, and the last to end leaves the summary of all of them.
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
        if (folder != null) {
            makeFolder(folder);
        }
        if (cantWriteIn(folder) || cantWriteOver(file)) {
            throw cantWrite(folder, "not writable");
        }
    }

    private static boolean cantWriteOver(final Path report) {
        return Files.exists(report) && !Files.isWritable(report);
    }

    private void makeFolder(final Path folder) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw cantWrite(folder, e.toString());
        }
    }

    private static boolean cantWriteIn(final Path folder) {
        return folder != null && !Files.isWritable(folder);
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

    /**
     * Rebuilds the summary beside the report from every line in it, under the report's lock. With no report there
     * were no calls, and an old summary is removed. If the summary can't be written it is removed too: a wrong
     * summary is worse than none, and the report itself is still whole. Called as the JVM ends.
     */
    public void summarise() {
        final Path summary = Summary.fileFor(file);
        try {
            writeSummary(summary);
        } catch (IOException cantWrite) {
            try {
                Files.deleteIfExists(summary);
            } catch (IOException cantRemove) {
                // The JVM is ending and the agent writes nothing but its report: there is nowhere left to say so.
                return;
            }
        }
    }

    private synchronized void writeSummary(final Path summary) throws IOException {
        if (!Files.exists(file)) {
            Files.deleteIfExists(summary);
            return;
        }
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
                FileLock lock = channel.lock()) {
            final Path temporary = summary.resolveSibling(summary.getFileName() + ".tmp");
            Files.write(temporary, Summary.of(String.valueOf(file.getFileName()), read(channel, lock))
                    .getBytes(StandardCharsets.UTF_8));
            Files.move(temporary, summary, StandardCopyOption.ATOMIC_MOVE);
        }
    }

    private static String read(final FileChannel channel, final FileLock lock) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
        int read = 0;
        while (buffer.hasRemaining() && read >= 0 && lock.isValid()) {
            read = channel.read(buffer);
        }
        return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
    }
}

package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.ChannelAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.CopyAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.FileChangeAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.FileCreateAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.FileRenameAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.FileStreamAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.MoveAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.PathAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.RandomAccessAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SecureChannelAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SecureDeleteAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SecureFolderAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.SecureMoveAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.TempFileAdvice;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.Set;

/**
 * What each file-write hook rewrites, and with which advice: the lowest points every route to opening a file to
 * write, deleting, renaming, moving or linking one, or making a folder, goes through. The default file system's class
 * differs between Windows and the rest, and is worked out on the running JDK.
 *
 * <ul>
 * <li>{@code STREAM}: {@code FileOutputStream}'s private {@code open}, which every constructor reaches, to write or to
 * append.</li>
 * <li>{@code RANDOM_ACCESS}: {@code RandomAccessFile}'s private {@code open}, which every constructor reaches; checked
 * if opened to write.</li>
 * <li>{@code CHANGE}: {@code File.delete} and {@code File.mkdir}, which {@code mkdirs} reaches for each folder.</li>
 * <li>{@code RENAME}, {@code CREATE}: {@code File.renameTo} and {@code File.createNewFile}.</li>
 * <li>{@code TEMP_FILE}: {@code File.createTempFile} with a folder, which the two-argument one reaches.</li>
 * <li>{@code CHANNEL}: the default file system's {@code newByteChannel}, {@code newFileChannel} and
 * {@code newAsynchronousFileChannel}, which {@code Files}' streams, writers and channels, and
 * {@code FileChannel.open}, reach.</li>
 * <li>{@code PATH}: the default file system's {@code createDirectory} (which {@code Files.createDirectories} reaches),
 * its delete (which {@code Files.delete} and {@code deleteIfExists} reach), {@code createSymbolicLink} and
 * {@code createLink}.</li>
 * <li>{@code COPY}, {@code MOVE}: the default file system's {@code copy} between two paths, and its {@code move}.</li>
 * <li>{@code SECURE_FOLDER}: the constructor of the JDK's secure directory stream (on Unix), which
 * {@code Files.newDirectoryStream} hands out where the system supports it, so the folder its paths are relative to is
 * known. Refuses nothing itself.</li>
 * <li>{@code SECURE_DELETE}, {@code SECURE_MOVE}, {@code SECURE_CHANNEL}: a secure directory stream's
 * {@code deleteFile} and {@code deleteDirectory}, its {@code move} to another's folder, and its
 * {@code newByteChannel}.</li>
 * </ul>
 */
public final class FileHooks {

    public static final HookSpec STREAM = new HookSpec("java.io.FileOutputStream",
            named("open").and(takesArguments(String.class, boolean.class)), FileStreamAdvice.class);
    public static final HookSpec RANDOM_ACCESS = new HookSpec("java.io.RandomAccessFile",
            named("open").and(takesArguments(String.class, int.class)), RandomAccessAdvice.class);
    public static final HookSpec CHANGE = new HookSpec("java.io.File",
            namedOneOf("delete", "mkdir").and(takesArguments(0)), FileChangeAdvice.class);
    public static final HookSpec RENAME = new HookSpec("java.io.File",
            named("renameTo").and(takesArguments(File.class)), FileRenameAdvice.class);
    public static final HookSpec CREATE = new HookSpec("java.io.File",
            named("createNewFile").and(takesArguments(0)), FileCreateAdvice.class);
    public static final HookSpec TEMP_FILE = new HookSpec("java.io.File",
            named("createTempFile").and(takesArguments(String.class, String.class, File.class)),
            TempFileAdvice.class);
    public static final HookSpec CHANNEL = new HookSpec(HookMethods.fileSystemProvider(),
            namedOneOf("newByteChannel", "newFileChannel", "newAsynchronousFileChannel")
                    .and(takesArgument(0, Path.class)).and(takesArgument(1, Set.class)), ChannelAdvice.class);
    public static final HookSpec PATH = new HookSpec(HookMethods.fileSystemProvider(),
            namedOneOf("createDirectory", "implDelete", "createSymbolicLink", "createLink")
                    .and(takesArgument(0, Path.class)), PathAdvice.class);
    public static final HookSpec COPY = new HookSpec(HookMethods.fileSystemProvider(),
            named("copy").and(takesArguments(3)).and(takesArgument(1, Path.class)), CopyAdvice.class);
    public static final HookSpec MOVE = new HookSpec(HookMethods.fileSystemProvider(),
            named("move").and(takesArguments(3)).and(takesArgument(1, Path.class)), MoveAdvice.class);
    public static final HookSpec SECURE_FOLDER = new HookSpec(HookMethods.SECURE_UNIX_DIRECTORY_STREAM,
            isConstructor().and(takesArguments(4)), SecureFolderAdvice.class);
    public static final HookSpec SECURE_DELETE = new HookSpec(HookMethods.SECURE_UNIX_DIRECTORY_STREAM,
            namedOneOf("deleteFile", "deleteDirectory").and(takesArguments(Path.class)), SecureDeleteAdvice.class);
    public static final HookSpec SECURE_MOVE = new HookSpec(HookMethods.SECURE_UNIX_DIRECTORY_STREAM,
            named("move").and(takesArguments(Path.class, SecureDirectoryStream.class, Path.class)),
            SecureMoveAdvice.class);
    public static final HookSpec SECURE_CHANNEL = new HookSpec(HookMethods.SECURE_UNIX_DIRECTORY_STREAM,
            named("newByteChannel").and(takesArguments(3)).and(takesArgument(0, Path.class)),
            SecureChannelAdvice.class);

    private FileHooks() {
    }
}

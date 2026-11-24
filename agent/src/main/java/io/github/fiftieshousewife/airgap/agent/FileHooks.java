package io.github.fiftieshousewife.airgap.agent;

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
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * What each file-write hook in {@link Hook} rewrites, and with which advice: the lowest points every route to opening
 * a file to write, deleting, renaming, moving or linking one, or making a folder, goes through. The default file
 * system's class differs between Windows and the rest, and is worked out on the running JDK.
 */
final class FileHooks {

    /** {@code FileOutputStream}'s private {@code open}, which every constructor reaches, to write or to append. */
    static final Spec STREAM = new Spec("java.io.FileOutputStream",
            named("open").and(takesArguments(String.class, boolean.class)), FileStreamAdvice.class);

    /** {@code RandomAccessFile}'s private {@code open}, which every constructor reaches; checked if opened to write. */
    static final Spec RANDOM_ACCESS = new Spec("java.io.RandomAccessFile",
            named("open").and(takesArguments(String.class, int.class)), RandomAccessAdvice.class);

    /** {@code File.delete} and {@code File.mkdir}, which {@code mkdirs} reaches for each folder it makes. */
    static final Spec CHANGE = new Spec("java.io.File", namedOneOf("delete", "mkdir").and(takesArguments(0)),
            FileChangeAdvice.class);

    /** {@code File.renameTo}. */
    static final Spec RENAME = new Spec("java.io.File", named("renameTo").and(takesArguments(File.class)),
            FileRenameAdvice.class);

    /** {@code File.createNewFile}. */
    static final Spec CREATE = new Spec("java.io.File", named("createNewFile").and(takesArguments(0)),
            FileCreateAdvice.class);

    /** {@code File.createTempFile} with a folder, which the two-argument one reaches. */
    static final Spec TEMP_FILE = new Spec("java.io.File",
            named("createTempFile").and(takesArguments(String.class, String.class, File.class)),
            TempFileAdvice.class);

    /**
     * The default file system's {@code newByteChannel}, {@code newFileChannel} and {@code newAsynchronousFileChannel},
     * which {@code Files}' streams, writers and channels, and {@code FileChannel.open}, reach.
     */
    static final Spec CHANNEL = new Spec(HookMethods.fileSystemProvider(),
            namedOneOf("newByteChannel", "newFileChannel", "newAsynchronousFileChannel")
                    .and(takesArgument(0, Path.class)).and(takesArgument(1, Set.class)), ChannelAdvice.class);

    /**
     * The default file system's {@code createDirectory} (which {@code Files.createDirectories} reaches), its delete
     * (which {@code Files.delete} and {@code deleteIfExists} reach), {@code createSymbolicLink} and {@code createLink}.
     */
    static final Spec PATH = new Spec(HookMethods.fileSystemProvider(),
            namedOneOf("createDirectory", "implDelete", "createSymbolicLink", "createLink")
                    .and(takesArgument(0, Path.class)), PathAdvice.class);

    /** The default file system's {@code copy}, between two paths. */
    static final Spec COPY = new Spec(HookMethods.fileSystemProvider(),
            named("copy").and(takesArguments(3)).and(takesArgument(1, Path.class)), CopyAdvice.class);

    /** The default file system's {@code move}. */
    static final Spec MOVE = new Spec(HookMethods.fileSystemProvider(),
            named("move").and(takesArguments(3)).and(takesArgument(1, Path.class)), MoveAdvice.class);

    /**
     * The JDK's secure directory stream (on Unix), which {@code Files.newDirectoryStream} hands out where the system
     * supports it: its constructor, so the folder its paths are relative to is known. Refuses nothing itself.
     */
    static final Spec SECURE_FOLDER = new Spec(HookMethods.SECURE_STREAM, isConstructor().and(takesArguments(4)),
            SecureFolderAdvice.class);

    /** A secure directory stream's {@code deleteFile} and {@code deleteDirectory}. */
    static final Spec SECURE_DELETE = new Spec(HookMethods.SECURE_STREAM,
            namedOneOf("deleteFile", "deleteDirectory").and(takesArguments(Path.class)), SecureDeleteAdvice.class);

    /** A secure directory stream's {@code move}, to another's folder. */
    static final Spec SECURE_MOVE = new Spec(HookMethods.SECURE_STREAM,
            named("move").and(takesArguments(Path.class, SecureDirectoryStream.class, Path.class)),
            SecureMoveAdvice.class);

    /** A secure directory stream's {@code newByteChannel}. */
    static final Spec SECURE_CHANNEL = new Spec(HookMethods.SECURE_STREAM,
            named("newByteChannel").and(takesArguments(3)).and(takesArgument(0, Path.class)),
            SecureChannelAdvice.class);

    private FileHooks() {
    }

    /** One hook's class, methods and advice. */
    static final class Spec {
        private final String typeName;
        private final ElementMatcher<? super MethodDescription> methods;
        private final Class<?> advice;

        Spec(final String typeName, final ElementMatcher<? super MethodDescription> methods, final Class<?> advice) {
            this.typeName = typeName;
            this.methods = methods;
            this.advice = advice;
        }

        String getTypeName() {
            return typeName;
        }

        ElementMatcher<? super MethodDescription> getMethods() {
            return methods;
        }

        Class<?> getAdvice() {
            return advice;
        }
    }
}

package io.github.fiftieshousewife.airgap.agent.hooks;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.github.fiftieshousewife.airgap.agent.advice.ForeignLibraryAdvice;
import io.github.fiftieshousewife.airgap.agent.advice.NativeLibraryAdvice;
import java.nio.file.Path;

/**
 * The native-library hooks: {@code System.loadLibrary} and {@code System.load} ({@code BY_NAME}, {@code BY_PATH}), the
 * same on {@code Runtime} ({@code RUNTIME_BY_NAME}, {@code RUNTIME_BY_PATH}), and JDK 22's foreign function API loading
 * a library by name or path: {@code SymbolLookup.libraryLookup(String, Arena)} and {@code (Path, Arena)}
 * ({@code FOREIGN_BY_NAME}, {@code FOREIGN_BY_PATH}).
 */
public final class NativeHooks {

    public static final HookSpec BY_NAME = new HookSpec("java.lang.System",
            named("loadLibrary").and(takesArguments(String.class)), NativeLibraryAdvice.class);
    public static final HookSpec BY_PATH = new HookSpec("java.lang.System",
            named("load").and(takesArguments(String.class)), NativeLibraryAdvice.class);
    public static final HookSpec RUNTIME_BY_NAME = new HookSpec("java.lang.Runtime",
            named("loadLibrary").and(takesArguments(String.class)), NativeLibraryAdvice.class);
    public static final HookSpec RUNTIME_BY_PATH = new HookSpec("java.lang.Runtime",
            named("load").and(takesArguments(String.class)), NativeLibraryAdvice.class);
    public static final HookSpec FOREIGN_BY_NAME = new HookSpec("java.lang.foreign.SymbolLookup",
            named("libraryLookup").and(takesArguments(2)).and(takesArgument(0, String.class)),
            ForeignLibraryAdvice.class);
    public static final HookSpec FOREIGN_BY_PATH = new HookSpec("java.lang.foreign.SymbolLookup",
            named("libraryLookup").and(takesArguments(2)).and(takesArgument(0, Path.class)),
            ForeignLibraryAdvice.class);

    private NativeHooks() {
    }
}

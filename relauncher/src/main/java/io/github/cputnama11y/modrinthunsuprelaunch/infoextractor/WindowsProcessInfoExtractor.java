package io.github.cputnama11y.modrinthunsuprelaunch.infoextractor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

// Human Note: SLOP, please review later and rewrite

/**
 * Extracts the current process's command and arguments on Windows using the
 * Foreign Function &amp; Memory API. Only supported on Windows; other operating
 * systems fail with a descriptive {@link FailedToFetchInfoException}.
 */
public final class WindowsProcessInfoExtractor implements ProcessInfoExtractor {
    private static final int INITIAL_PATH_CHARS = 260;       // MAX_PATH
    private static final int MAX_PATH_CHARS = 32_768;        // long-path limit

    private record Natives(
            MethodHandle getModuleFileNameW,
            MethodHandle getCommandLineW,
            MethodHandle commandLineToArgvW,
            MethodHandle localFree) {}

    private volatile Natives natives;

    /** Returns true if the current OS is Windows. */
    public static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).startsWith("windows");
    }

    @Override
    public String getCommand() throws FailedToFetchInfoException {
        Natives n = natives();

        try (Arena arena = Arena.ofConfined()) {
            // GetModuleFileNameW truncates silently; if the result fills the
            // buffer, grow it and try again.
            for (int chars = INITIAL_PATH_CHARS; chars <= MAX_PATH_CHARS; chars *= 2) {
                MemorySegment buf = arena.allocate((long) chars * Character.BYTES);
                int len = (int) n.getModuleFileNameW().invokeExact(MemorySegment.NULL, buf, chars);
                if (len <= 0) {
                    throw new FailedToFetchInfoException("GetModuleFileNameW failed");
                }
                if (len < chars) {
                    return buf.getString(0, StandardCharsets.UTF_16LE);
                }
            }
            throw new FailedToFetchInfoException(
                    "Executable path is longer than " + MAX_PATH_CHARS + " characters");
        } catch (FailedToFetchInfoException e) {
            throw e;
        } catch (Throwable t) {
            throw new FailedToFetchInfoException("Failed to call GetModuleFileNameW", t);
        }
    }

    @Override
    public String[] getArguments() throws FailedToFetchInfoException {
        Natives n = natives();
        List<String> argv = fetchArgv(n);
        if (argv.isEmpty()) {
            throw new FailedToFetchInfoException("Process argument list is empty");
        }
        return argv.subList(1, argv.size()).toArray(String[]::new); // drop argv[0]
    }

    // ---------------------------------------------------------------- native

    private Natives natives() throws FailedToFetchInfoException {
        if (!isWindows()) {
            throw new FailedToFetchInfoException(
                    "WindowsProcessInfoExtractor only supports Windows, but the current operating system is '"
                            + System.getProperty("os.name", "unknown") + "'");
        }
        Natives local = natives;
        if (local == null) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
                SymbolLookup shell32 = SymbolLookup.libraryLookup("shell32", Arena.global());

                // DWORD GetModuleFileNameW(HMODULE, LPWSTR, DWORD)
                MethodHandle getModuleFileNameW = linker.downcallHandle(
                        kernel32.find("GetModuleFileNameW").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

                // LPWSTR GetCommandLineW(void)
                MethodHandle getCommandLineW = linker.downcallHandle(
                        kernel32.find("GetCommandLineW").orElseThrow(),
                        FunctionDescriptor.of(ADDRESS));

                // LPWSTR* CommandLineToArgvW(LPCWSTR, int*)
                MethodHandle commandLineToArgvW = linker.downcallHandle(
                        shell32.find("CommandLineToArgvW").orElseThrow(),
                        FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

                // HLOCAL LocalFree(HLOCAL)
                MethodHandle localFree = linker.downcallHandle(
                        kernel32.find("LocalFree").orElseThrow(),
                        FunctionDescriptor.of(ADDRESS, ADDRESS));

                local = new Natives(getModuleFileNameW, getCommandLineW, commandLineToArgvW, localFree);
                natives = local;
            } catch (Throwable t) {
                throw new FailedToFetchInfoException("Failed to link native Windows functions", t);
            }
        }
        return local;
    }

    private static List<String> fetchArgv(Natives n) throws FailedToFetchInfoException {
        MemorySegment argvArray = MemorySegment.NULL;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment cmdLine = (MemorySegment) n.getCommandLineW().invokeExact();
            if (cmdLine.equals(MemorySegment.NULL)) {
                throw new FailedToFetchInfoException("GetCommandLineW returned null");
            }

            MemorySegment argcOut = arena.allocate(JAVA_INT);
            argvArray = (MemorySegment) n.commandLineToArgvW().invokeExact(cmdLine, argcOut);
            if (argvArray.equals(MemorySegment.NULL)) {
                throw new FailedToFetchInfoException("CommandLineToArgvW failed");
            }

            int argc = argcOut.get(JAVA_INT, 0);
            MemorySegment ptrs = argvArray.reinterpret((long) argc * ADDRESS.byteSize());

            List<String> argv = new ArrayList<>(argc);
            for (int i = 0; i < argc; i++) {
                MemorySegment str = ptrs.getAtIndex(ADDRESS, i).reinterpret(Long.MAX_VALUE);
                argv.add(str.getString(0, StandardCharsets.UTF_16LE));
            }
            return argv;
        } catch (FailedToFetchInfoException e) {
            throw e;
        } catch (Throwable t) {
            throw new FailedToFetchInfoException("Failed to read the process command line", t);
        } finally {
            if (!argvArray.equals(MemorySegment.NULL)) {
                try {
                    MemorySegment ignored = (MemorySegment) n.localFree().invokeExact(argvArray);
                } catch (Throwable ignored) {
                    // nothing sensible to do if freeing fails
                }
            }
        }
    }
}
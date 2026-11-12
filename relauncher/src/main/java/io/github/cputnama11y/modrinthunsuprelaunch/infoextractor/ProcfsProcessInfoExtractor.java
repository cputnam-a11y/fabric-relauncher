package io.github.cputnama11y.modrinthunsuprelaunch.infoextractor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

// Human Note: SLOP, please review later and rewrite

/**
 * Extracts the current process's command and arguments from Linux procfs.
 * Only supported on Linux; other operating systems fail with a descriptive
 * {@link FailedToFetchInfoException}.
 */
public final class ProcfsProcessInfoExtractor implements ProcessInfoExtractor {
    private static final Path PROC_SELF = Path.of("/proc/self");
    private static final Path EXE = PROC_SELF.resolve("exe");
    private static final Path CMDLINE = PROC_SELF.resolve("cmdline");

    // Linux stores argv as raw bytes; the JVM decodes them with sun.jnu.encoding.
    private static final Charset ARG_CHARSET = Charset.forName(
            System.getProperty("sun.jnu.encoding", Charset.defaultCharset().name()));

    /** Returns true if the current OS is Linux. */
    public static boolean isLinux() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).startsWith("linux");
    }

    /** Fails with a descriptive message if procfs-based extraction cannot work here. */
    private static void ensureSupported() throws FailedToFetchInfoException {
        if (!isLinux()) {
            throw new FailedToFetchInfoException(
                    "ProcfsProcessInfoExtractor only supports Linux, but the current operating system is '"
                            + System.getProperty("os.name", "unknown") + "'");
        }
        if (!Files.isDirectory(PROC_SELF)) {
            throw new FailedToFetchInfoException(
                    PROC_SELF + " does not exist; is procfs mounted at /proc?");
        }
    }

    @Override
    public String getCommand() throws FailedToFetchInfoException {
        ensureSupported();
        try {
            // Resolved absolute path of the actual executable (e.g. .../bin/java)
            return Files.readSymbolicLink(EXE).toString();
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            // exe can be unreadable (e.g. restricted procfs); fall back to argv[0]
            try {
                List<String> argv = readCmdline();
                if (argv.isEmpty()) {
                    throw new FailedToFetchInfoException(CMDLINE + " is empty");
                }
                return argv.getFirst();
            } catch (FailedToFetchInfoException inner) {
                inner.addSuppressed(e);
                throw inner;
            }
        }
    }

    @Override
    public String[] getArguments() throws FailedToFetchInfoException {
        ensureSupported();
        List<String> argv = readCmdline();
        if (argv.isEmpty()) {
            throw new FailedToFetchInfoException(CMDLINE + " is empty");
        }
        return argv.subList(1, argv.size()).toArray(String[]::new);
    }

    private static List<String> readCmdline() throws FailedToFetchInfoException {
        byte[] raw;
        try {
            raw = Files.readAllBytes(CMDLINE);
        } catch (IOException | SecurityException e) {
            throw new FailedToFetchInfoException("Failed to read " + CMDLINE, e);
        }
        return splitNul(raw);
    }

    /** Splits NUL-terminated entries, preserving legitimately empty arguments. */
    private static List<String> splitNul(byte[] raw) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                out.add(new String(Arrays.copyOfRange(raw, start, i), ARG_CHARSET));
                start = i + 1;
            }
        }
        if (start < raw.length) { // unterminated trailing entry
            out.add(new String(Arrays.copyOfRange(raw, start, raw.length), ARG_CHARSET));
        }
        return out;
    }
}
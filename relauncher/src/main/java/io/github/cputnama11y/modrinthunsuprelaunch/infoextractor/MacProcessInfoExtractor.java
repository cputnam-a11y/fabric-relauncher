package io.github.cputnama11y.modrinthunsuprelaunch.infoextractor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

// Human Note: SLOP, please review later and rewrite

/**
 * Extracts the current process's command and arguments on macOS using the
 * Foreign Function &amp; Memory API. Only supported on macOS; other operating
 * systems fail with a descriptive {@link FailedToFetchInfoException}.
 */
public final class MacProcessInfoExtractor implements ProcessInfoExtractor {
    private static final int CTL_KERN = 1;
    private static final int KERN_PROCARGS2 = 49;
    private static final int PROC_PIDPATHINFO_MAXSIZE = 4096; // 4 * MAXPATHLEN
    private static final int ENOMEM = 12;
    private static final int MAX_ATTEMPTS = 4;

    private static final Charset ARG_CHARSET = Charset.forName(
            System.getProperty("sun.jnu.encoding", Charset.defaultCharset().name()));

    private record Natives(MethodHandle sysctl, MethodHandle procPidPath, VarHandle errno) {}

    private volatile Natives natives;

    /** Returns true if the current OS is macOS. */
    public static boolean isMac() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).contains("mac") || os.toLowerCase(Locale.ROOT).contains("darwin");
    }

    @Override
    public String getCommand() throws FailedToFetchInfoException {
        Natives n = natives();
        int pid = currentPid();

        // Preferred: the kernel's resolved, absolute executable path.
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buf = arena.allocate(PROC_PIDPATHINFO_MAXSIZE);
            int len = (int) n.procPidPath().invokeExact(pid, buf, PROC_PIDPATHINFO_MAXSIZE);
            if (len > 0) {
                return buf.getString(0, ARG_CHARSET);
            }
        } catch (Throwable ignored) {
            // fall through to the procargs exec_path
        }

        try {
            return parseProcArgs(fetchProcArgs(n, pid)).execPath();
        } catch (FailedToFetchInfoException e) {
            throw new FailedToFetchInfoException("Failed to determine the executable path", e);
        }
    }

    @Override
    public String[] getArguments() throws FailedToFetchInfoException {
        Natives n = natives();
        List<String> argv = parseProcArgs(fetchProcArgs(n, currentPid())).argv();
        if (argv.isEmpty()) {
            throw new FailedToFetchInfoException("Process argument list is empty");
        }
        return argv.subList(1, argv.size()).toArray(String[]::new); // drop argv[0]
    }

    // ---------------------------------------------------------------- native

    private Natives natives() throws FailedToFetchInfoException {
        if (!isMac()) {
            throw new FailedToFetchInfoException(
                    "MacProcessInfoExtractor only supports macOS, but the current operating system is '"
                            + System.getProperty("os.name", "unknown") + "'");
        }
        Natives local = natives;
        if (local == null) {
            try {
                Linker linker = Linker.nativeLinker();
                SymbolLookup lookup = linker.defaultLookup();
                Linker.Option captureErrno = Linker.Option.captureCallState("errno");

                // int sysctl(int *name, u_int namelen, void *oldp, size_t *oldlenp, void *newp, size_t newlen)
                MethodHandle sysctl = linker.downcallHandle(
                        lookup.find("sysctl").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG),
                        captureErrno);

                // int proc_pidpath(int pid, void *buffer, uint32_t buffersize)
                MethodHandle procPidPath = linker.downcallHandle(
                        lookup.find("proc_pidpath").orElseThrow(),
                        FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));

                VarHandle errno = Linker.Option.captureStateLayout()
                        .varHandle(java.lang.foreign.MemoryLayout.PathElement.groupElement("errno"));

                local = new Natives(sysctl, procPidPath, errno);
                natives = local;
            } catch (Throwable t) { // includes IllegalCallerException / UnsupportedOperationException
                throw new FailedToFetchInfoException("Failed to link native macOS functions", t);
            }
        }
        return local;
    }

    private static int currentPid() {
        return (int) ProcessHandle.current().pid();
    }

    /** Reads the raw KERN_PROCARGS2 buffer for the given pid. */
    private static byte[] fetchProcArgs(Natives n, int pid) throws FailedToFetchInfoException {
        int[] mibValues = {CTL_KERN, KERN_PROCARGS2, pid};

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mib = arena.allocateFrom(JAVA_INT, mibValues);
            MemorySegment size = arena.allocate(JAVA_LONG);
            MemorySegment state = arena.allocate(Linker.Option.captureStateLayout());

            for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
                // 1) Ask for the required size.
                size.set(JAVA_LONG, 0, 0L);
                int rc = (int) n.sysctl().invokeExact(
                        state, mib, mibValues.length, MemorySegment.NULL, size, MemorySegment.NULL, 0L);
                if (rc != 0) {
                    throw new FailedToFetchInfoException(
                            "sysctl(KERN_PROCARGS2) size query failed, errno=" + (int) n.errno().get(state, 0L));
                }

                // 2) Fetch the data (with a little slack in case it grew).
                long capacity = size.get(JAVA_LONG, 0) + 256;
                MemorySegment buf = arena.allocate(capacity);
                size.set(JAVA_LONG, 0, capacity);

                rc = (int) n.sysctl().invokeExact(
                        state, mib, mibValues.length, buf, size, MemorySegment.NULL, 0L);
                if (rc == 0) {
                    long actual = size.get(JAVA_LONG, 0);
                    return buf.asSlice(0, actual).toArray(JAVA_BYTE);
                }

                int errno = (int) n.errno().get(state, 0L);
                if (errno != ENOMEM) {
                    throw new FailedToFetchInfoException("sysctl(KERN_PROCARGS2) failed, errno=" + errno);
                }
                // ENOMEM: the arguments grew between calls; retry.
            }
            throw new FailedToFetchInfoException("sysctl(KERN_PROCARGS2) kept returning ENOMEM");
        } catch (FailedToFetchInfoException e) {
            throw e;
        } catch (Throwable t) {
            throw new FailedToFetchInfoException("Failed to call sysctl(KERN_PROCARGS2)", t);
        }
    }

    // --------------------------------------------------------------- parsing

    private record ProcArgs(String execPath, List<String> argv) {}

    /**
     * Layout of KERN_PROCARGS2:
     * <pre>
     *   int   argc
     *   char  exec_path[]  (NUL-terminated)
     *   char  padding[]    (NULs)
     *   char  argv[0..argc-1][]  (each NUL-terminated)
     *   char  envp[]...
     * </pre>
     */
    private static ProcArgs parseProcArgs(byte[] data) throws FailedToFetchInfoException {
        if (data.length < Integer.BYTES) {
            throw new FailedToFetchInfoException("KERN_PROCARGS2 buffer is too short");
        }

        int argc = java.nio.ByteBuffer.wrap(data).order(ByteOrder.nativeOrder()).getInt();
        int pos = Integer.BYTES;

        int pathEnd = indexOfNul(data, pos);
        String execPath = new String(data, pos, pathEnd - pos, ARG_CHARSET);
        pos = pathEnd;

        // Skip the NUL padding between exec_path and argv[0].
        while (pos < data.length && data[pos] == 0) {
            pos++;
        }

        List<String> argv = new ArrayList<>(Math.max(argc, 0));
        for (int i = 0; i < argc && pos < data.length; i++) {
            int end = indexOfNul(data, pos);
            argv.add(new String(data, pos, end - pos, ARG_CHARSET));
            pos = end + 1;
        }

        if (argv.size() != argc) {
            throw new FailedToFetchInfoException(
                    "Expected " + argc + " arguments but only parsed " + argv.size());
        }
        return new ProcArgs(execPath, argv);
    }

    private static int indexOfNul(byte[] data, int from) {
        for (int i = from; i < data.length; i++) {
            if (data[i] == 0) {
                return i;
            }
        }
        return data.length; // unterminated; treat the end of the buffer as the terminator
    }
}
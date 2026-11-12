package io.github.cputnama11y.modrinthunsuprelaunch.relauncher;

import io.github.cputnama11y.modrinthunsuprelaunch.infoextractor.ProcessInfoExtractor;
import io.github.cputnama11y.modrinthunsuprelaunch.util.ArgumentPreprocessor;
import io.github.cputnama11y.modrinthunsuprelaunch.util.Registry;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

// Human Note: SLOP, please review later and rewrite

public final class ExecvRelauncher
        implements Relauncher<ExecvRelauncher.LaunchState> {

    private static final String OS =
            System.getProperty("os.name", "");

    private static final boolean SUPPORTED =
            OS.equals("Mac OS X") || OS.equals("Linux");

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle EXECV = resolveExecv();


    public ExecvRelauncher() {
    }

    private static MethodHandle resolveExecv() {
        if (!SUPPORTED) {
            return null;
        }

        try {
            return LINKER.downcallHandle(
                    LINKER.defaultLookup()
                            .find("execv")
                            .orElseThrow(),
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS,
                            ValueLayout.ADDRESS
                    )
            );
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    public static final class LaunchState {
        private final Arena arena;
        private final MemorySegment executable;
        private final MemorySegment argv;

        private LaunchState(
                Arena arena,
                MemorySegment executable,
                MemorySegment argv
        ) {
            this.arena = arena;
            this.executable = executable;
            this.argv = argv;
        }
    }

    @Override
    public LaunchState extract() throws ExtractionFailedException {
        if (!SUPPORTED) {
            throw new ExtractionFailedException(
                    "execv is unsupported on platform: " + OS
            );
        }

        Arena arena = Arena.ofShared();

        try {
            var command = Registry.getApplicableProcessInfoExtractors().stream().flatMap(
                    extractor -> {
                        try {
                            return Optional.of(extractor.getCommand()).stream();
                        } catch (ProcessInfoExtractor.FailedToFetchInfoException e) {
                            return Stream.of();
                        }
                    }
            ).findFirst().orElseThrow(() -> new ExtractionFailedException("Failed To Get Process Command"));
            var arguments = Registry.getApplicableProcessInfoExtractors().stream().flatMap(
                    extractor -> {
                        try {
                            return Optional.of(extractor.getArguments()).stream();
                        } catch (ProcessInfoExtractor.FailedToFetchInfoException e) {
                            return Stream.of();
                        }
                    }
            ).findFirst().orElseThrow(() -> new ExtractionFailedException("Failed To Get Process Command"));
            arguments = ArgumentPreprocessor.buildCommand(command, arguments);
            List<String> allArguments = List.of(arguments);

            MemorySegment executable = arena.allocateFrom(command);

            MemorySegment argv = arena.allocate(
                    ValueLayout.ADDRESS,
                    (long) allArguments.size() + 1
            );

            for (int i = 0; i < allArguments.size(); i++) {
                argv.setAtIndex(
                        ValueLayout.ADDRESS,
                        i,
                        arena.allocateFrom(allArguments.get(i))
                );
            }

            argv.setAtIndex(
                    ValueLayout.ADDRESS,
                    allArguments.size(),
                    MemorySegment.NULL
            );

            return new LaunchState(arena, executable, argv);
        } catch (Throwable t) {
            arena.close();

            throw new ExtractionFailedException(
                    "Unable to prepare execv launch state",
                    t
            );
        }
    }

    @Override
    public void relaunch(
            RelaunchParameters<LaunchState> params
    ) throws RelaunchFailedException {
        LaunchState state = params.getLaunchParams();

        try {
            // Old-process cleanup must not invalidate LaunchState.
            params.cleanup();

            int errno = (int) EXECV.invokeExact(
                    state.executable,
                    state.argv
            );

            throw new RelaunchFailedException(
                    "execv returned unexpectedly; errno=" + errno
            );
        } catch (RelaunchFailedException e) {
            throw e;
        } catch (Throwable t) {
            throw new RelaunchFailedException(
                    "Unable to invoke execv",
                    t
            );
        }
    }
}

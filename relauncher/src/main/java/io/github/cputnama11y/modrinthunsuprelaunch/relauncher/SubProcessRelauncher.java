package io.github.cputnama11y.modrinthunsuprelaunch.relauncher;

import io.github.cputnama11y.modrinthunsuprelaunch.util.ArgumentPreprocessor;
import io.github.cputnama11y.modrinthunsuprelaunch.util.Cleanup;
import io.github.cputnama11y.modrinthunsuprelaunch.util.Registry;
import io.github.cputnama11y.modrinthunsuprelaunch.infoextractor.ProcessInfoExtractor;

import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

public record SubProcessRelauncher() implements Relauncher<SubProcessRelauncher.SubProcessParameters> {
    @Override
    public SubProcessParameters extract() throws ExtractionFailedException {
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
        try {
            return new SubProcessParameters(ArgumentPreprocessor.buildCommand(command, arguments));
        } catch (ArgumentPreprocessor.BuildCommandException e) {
            throw new ExtractionFailedException(e);
        }
    }

    public void relaunch(RelaunchParameters<SubProcessParameters> params) throws RelaunchFailedException {
        try {
            params.cleanup().cleanup(new Cleanup.ParamsAndRelauncher<>(this, params));
            var process = new ProcessBuilder(params.getLaunchParams().command()).inheritIO().start();
            while (true) {
                try {
                    process.waitFor();
                    break;
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
            System.exit(process.exitValue());
        } catch (IOException io) {
            throw new RelaunchFailedException("Failed to relaunch Minecraft: ", io);
        } catch (Cleanup.CleanupException _) {
        }
    }

    public record SubProcessParameters(String[] command) {

    }

}

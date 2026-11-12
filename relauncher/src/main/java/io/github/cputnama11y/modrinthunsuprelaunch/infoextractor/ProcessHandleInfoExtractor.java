package io.github.cputnama11y.modrinthunsuprelaunch.infoextractor;

public record ProcessHandleInfoExtractor() implements ProcessInfoExtractor {
    @Override
    public String getCommand() throws FailedToFetchInfoException {
        return ProcessHandle.current().info().command().orElseThrow(() -> new FailedToFetchInfoException("ProcessHandle.info has no command"));
    }

    @Override
    public String[] getArguments() throws FailedToFetchInfoException {
        return ProcessHandle.current().info().arguments().orElseThrow(() -> new FailedToFetchInfoException("ProcessHandle.info has no arguments"));
    }
}

package io.github.cputnama11y.modrinthunsuprelaunch.infoextractor;

public interface ProcessInfoExtractor {
    String getCommand() throws FailedToFetchInfoException;

    String[] getArguments() throws FailedToFetchInfoException;

    class FailedToFetchInfoException extends Exception {
        public FailedToFetchInfoException() {
        }

        public FailedToFetchInfoException(String message) {
            super(message);
        }

        public FailedToFetchInfoException(String message, Throwable cause) {
            super(message, cause);
        }

        public FailedToFetchInfoException(Throwable cause) {
            super(cause);
        }
    }
}

package io.github.cputnama11y.modrinthunsuprelaunch.relauncher;

import io.github.cputnama11y.modrinthunsuprelaunch.util.Cleanup;

public interface Relauncher<T> {
    T extract() throws ExtractionFailedException;

    void relaunch(RelaunchParameters<T> params) throws RelaunchFailedException;

    class ExtractionFailedException extends Exception {
        public ExtractionFailedException() {
        }

        public ExtractionFailedException(String message) {
            super(message);
        }

        public ExtractionFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        public ExtractionFailedException(Throwable cause) {
            super(cause);
        }
    }

    class RelaunchFailedException extends Exception {
        public RelaunchFailedException() {
        }

        public RelaunchFailedException(String message) {
            super(message);
        }

        public RelaunchFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        public RelaunchFailedException(Throwable cause) {
            super(cause);
        }
    }

    interface RelaunchParameters<T> {
        T getLaunchParams();

        Cleanup cleanup();
    }
}

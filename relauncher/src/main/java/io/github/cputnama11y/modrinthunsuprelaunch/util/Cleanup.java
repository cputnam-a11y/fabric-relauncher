package io.github.cputnama11y.modrinthunsuprelaunch.util;

import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.Relauncher;

import java.util.Optional;

public interface Cleanup {
    void cleanup(ParamsAndRelauncher<?> relaunchSetup) throws CleanupException;

    class CleanupException extends Exception {
        public CleanupException() {
        }

        public CleanupException(String message) {
            super(message);
        }

        public CleanupException(String message, Throwable cause) {
            super(message, cause);
        }

        public CleanupException(Throwable cause) {
            super(cause);
        }

        public CleanupException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    record ParamsAndRelauncher<T>(Relauncher<T> relauncher, Relauncher.RelaunchParameters<T> params) {
        @SuppressWarnings("unchecked")
        public <A> Optional<ParamsAndRelauncher<A>> asClass(Class<? extends Relauncher<A>> targetClass) {
            if (targetClass.isInstance(relauncher))
                return Optional.of(new ParamsAndRelauncher<A>((Relauncher<A>) relauncher, (Relauncher.RelaunchParameters<A>) params));
            return Optional.empty();
        }
    }

    public static Cleanup composite(Cleanup... cleanups) {
        return ctx -> {
            CleanupException ex2 = null;
            for (var cleanup : cleanups) {
                try {
                    cleanup.cleanup(ctx);
                } catch (CleanupException ex) {
                    if (ex2 == null) ex2 = ex;
                    else ex2.addSuppressed(ex);
                }
            }
            if (ex2 != null) {
                throw ex2;
            }
        };
    }
}

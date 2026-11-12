package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.TaskExecutor;

import java.util.ArrayList;
import java.util.List;

public interface TaskExecutorImpl extends TaskExecutor {
    static TaskExecutorImpl create(List<Task> tasks) {
        return instrumentation -> {
            var deferred = new ArrayList<Task>();
            try {
                ScopedValue.where(DeferUntilFail.DEFERRED_TASKS, deferred).run(() -> {
                    for (var task : tasks) {
                        task.run(instrumentation);
                    }
                });
            } catch (Throwable e) {
                deferred.forEach(t -> t.run(instrumentation));
                throw _throw(e);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException _throw(Throwable t) throws T {
        throw (T) t;
    }
}

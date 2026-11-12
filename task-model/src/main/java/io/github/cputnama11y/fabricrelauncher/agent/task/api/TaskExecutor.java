package io.github.cputnama11y.fabricrelauncher.agent.task.api;

import io.github.cputnama11y.fabricrelauncher.agent.task.impl.TaskExecutorImpl;
import org.jetbrains.annotations.ApiStatus;

import java.lang.instrument.Instrumentation;
import java.util.List;

@ApiStatus.NonExtendable
public interface TaskExecutor {
    void run(Instrumentation instrumentation);

    static TaskExecutor create(List<Task> tasks) {
        return TaskExecutorImpl.create(tasks);
    }
}

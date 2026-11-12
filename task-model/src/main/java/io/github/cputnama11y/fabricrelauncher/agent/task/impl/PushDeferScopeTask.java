package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.TaskExecutor;

import java.lang.instrument.Instrumentation;
import java.util.List;

public record PushDeferScopeTask(List<Task> wrapped) implements Task {
    public static final MapCodec<PushDeferScopeTask> CODEC = Task.CODEC.listOrSingle().fieldOf("wrapped").xmap(
            PushDeferScopeTask::new,
            PushDeferScopeTask::wrapped
    );

    @Override
    public void run(Instrumentation instrumentation) {
        TaskExecutor.create(this.wrapped).run(instrumentation);
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;

import java.lang.instrument.Instrumentation;
import java.util.List;

public record DeferUntilFail(List<Task> task) implements Task {
    public static final MapCodec<DeferUntilFail> CODEC = Task.CODEC.listOrSingle().fieldOf("deferred").xmap(
            DeferUntilFail::new,
            DeferUntilFail::task
    );
    public static final ScopedValue<List<Task>> DEFERRED_TASKS = ScopedValue.newInstance();

    @Override
    public void run(Instrumentation instrumentation) {
        DEFERRED_TASKS.orElseThrow(() -> new IllegalStateException("No deferred task context found")).addAll(task);
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

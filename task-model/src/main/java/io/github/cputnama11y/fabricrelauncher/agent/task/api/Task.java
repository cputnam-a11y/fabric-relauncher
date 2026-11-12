package io.github.cputnama11y.fabricrelauncher.agent.task.api;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.util.Id;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.util.SimpleRegistry;
import io.github.cputnama11y.fabricrelauncher.agent.task.impl.*;

import java.lang.instrument.Instrumentation;
import java.util.List;

public interface Task {
    SimpleRegistry<MapCodec<? extends Task>> REGISTRY = SimpleRegistry.create("fabricrelauncher");
    Codec<Task> CODEC = Codec.lazy(() -> {
        REGISTRY.register(id("copy_files"), CopyFilesTask.CODEC);
        REGISTRY.register(id("on_fail"), DeferUntilFail.CODEC);
        REGISTRY.register(id("delete_files"), DeleteFilesTask.CODEC);
        REGISTRY.register(id("append_fabric_properties"), FabricModsSystemPropertyConfigTask.CODEC);
        REGISTRY.register(id("catch_scope"), PushDeferScopeTask.CODEC);
        REGISTRY.register(id("java_agent"), RunAgentTask.CODEC);
        return Codec.codecDispatch(REGISTRY.byIdCodec(), Task::codec);
    });

    void run(Instrumentation instrumentation);

    MapCodec<? extends Task> codec();
    private static Id id(String path) {
        return new Id("fabricrelauncher", path);
    }

    static Task configureFabricSystemProperties(List<String> disabledMods, List<String> addModsSegments) {
        return new FabricModsSystemPropertyConfigTask(disabledMods, addModsSegments);
    }
}

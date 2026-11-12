package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.CompositeCodec;
import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;

import java.lang.instrument.Instrumentation;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record FabricModsSystemPropertyConfigTask(List<String> disabledMods, List<String> addModsSegments) implements Task {
    public static final MapCodec<FabricModsSystemPropertyConfigTask> CODEC = CompositeCodec.of(
            Codec.STRING.listOf().fieldOf("disabled_mods"),
            FabricModsSystemPropertyConfigTask::disabledMods,
            Codec.STRING.listOf().fieldOf("add_mods"),
            FabricModsSystemPropertyConfigTask::addModsSegments,
            FabricModsSystemPropertyConfigTask::new
    ).mapCodec();

    @Override
    public void run(Instrumentation instrumentation) {
        var disabledMods = System.getProperty("fabric.debug.disableModIds");
        System.setProperty("fabric.debug.disableModIds", Stream.concat(this.disabledMods().stream(), Optional.ofNullable(disabledMods).stream()).collect(Collectors.joining(",")));
        var addedMods = System.getProperty("fabric.addMods");
        System.setProperty("fabric.addMods", Stream.concat(addModsSegments.stream(), Optional.ofNullable(addedMods).stream()).collect(Collectors.joining(",")));
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

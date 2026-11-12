package io.github.cputnama11y.modrinthunsuprelaunch.util;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.CompositeCodec;
import fish.cichlidmc.tinyjson.TinyJson;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;
import io.github.cputnama11y.modrinthunsuprelaunch.Agent;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ArgumentPreprocessor {
    private static Path getAgentPath() throws BuildCommandException {
        try {
            Path agentJar = FabricLoader.getInstance().getModContainer("fabric-relauncher").orElseThrow(() -> new BuildCommandException("Failed to find mod container")).findPath("META-INF/agent/agent.jar").orElseThrow(() -> new BuildCommandException("Failed to find agent jar"));
            return Files.copy(agentJar, Files.createTempDirectory("relauncher").resolve("./agent.jar"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException e) {
            throw new BuildCommandException(e);
        }
    }

    private static Config readConfig() throws BuildCommandException {
        try {
            var configFile = FabricLoader.getInstance()
                    .getConfigDir()
                    .resolve("fabric-relauncher.json");
            if (Files.exists(configFile)) {
                var result = Config.CODEC.decode(TinyJson.parse(configFile));
                return result.valueOrThrow();
            } else return new Config(List.of(), List.of());
        } catch (IOException io) {
            throw new BuildCommandException(io);
        }
    }

    public static String[] buildCommand(String command, String[] arguments) throws BuildCommandException {
        var newCommand = new ArrayList<String>();
        newCommand.add(command);
        Config config = readConfig();
        var tasks = new ArrayList<>(config.tasks);
        tasks.addFirst(Task.configureFabricSystemProperties(List.of("fabric-relauncher"), List.of()));
        newCommand.add("-javaagent:" + getAgentPath() + "=" + Agent.encodeArguments(tasks));
        newCommand.addAll(config.freeJvmArguments);
        newCommand.addAll(Arrays.asList(arguments));
        return newCommand.toArray(String[]::new);
    }

    public static class BuildCommandException extends Exception {
        public BuildCommandException() {
        }

        public BuildCommandException(String message) {
            super(message);
        }

        public BuildCommandException(String message, Throwable cause) {
            super(message, cause);
        }

        public BuildCommandException(Throwable cause) {
            super(cause);
        }
    }

    record Config(List<Task> tasks, List<String> freeJvmArguments) {
        public static final Codec<Config> CODEC = CompositeCodec.of(
                Task.CODEC.listOf().optional(List.of()).fieldOf("tasks"),
                Config::tasks,
                Codec.STRING.listOf().optional(List.of()).fieldOf("jvm_arguments"),
                Config::freeJvmArguments,
                Config::new
        ).codec();
    }
}

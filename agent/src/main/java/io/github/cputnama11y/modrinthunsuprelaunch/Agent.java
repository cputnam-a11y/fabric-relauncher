package io.github.cputnama11y.modrinthunsuprelaunch;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinyjson.TinyJson;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.TaskExecutor;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

public class Agent {
    public static void premain(String arguments, Instrumentation inst) throws IOException {
        TaskExecutor.create(decodeArguments(arguments)).run(inst);
    }

    private static final Codec<List<Task>> CODEC = Task.CODEC.listOf().fieldOf("tasks").asCodec();

    public static String encodeArguments(List<Task> tasks) {
        return Base64.getEncoder().encodeToString(
                CODEC.encode(tasks)
                        .valueOrThrow()
                        .toString()
                        .getBytes(StandardCharsets.UTF_8)
        );
    }

    public static List<Task> decodeArguments(String arguments) {
        try {
            return CODEC.decode(
                            TinyJson.parse(
                                    new String(
                                            Base64.getDecoder().decode(arguments),
                                            StandardCharsets.UTF_8
                                    )
                            )
                    )
                    .valueOrThrow();
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse arguments:", e);
        }
    }
}

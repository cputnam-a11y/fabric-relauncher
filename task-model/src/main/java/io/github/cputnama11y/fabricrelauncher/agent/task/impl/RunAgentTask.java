package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.fishflakes.api.value.Either;
import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.CompositeCodec;
import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;
import org.jspecify.annotations.Nullable;

import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@SuppressWarnings("CallToPrintStackTrace")
public record RunAgentTask(String agentPath, String agentArgs) implements Task {
    public static final MapCodec<RunAgentTask> CODEC = CompositeCodec.of(
            Codec.STRING.fieldOf("agent_path"),
            RunAgentTask::agentPath,
            Codec.STRING.fieldOf("agent_args"),
            RunAgentTask::agentPath,
            RunAgentTask::new
    ).mapCodec();

    @Override
    public void run(Instrumentation instrumentation) {
        try (var jar = new JarFile(this.agentPath)) {
            var manifest = jar.getManifest();
            if (manifest == null)
                throw new IllegalStateException("No manifest found for agent " + this.agentPath);

            var agentClassName = getAgentClassForJar(manifest);
            if (agentClassName == null)
                throw new IllegalStateException("Failed to find agent class for agent " + this.agentPath);

            instrumentation.appendToSystemClassLoaderSearch(new JarFile(this.agentPath));
            var agentClass = Class.forName(agentClassName, true, ClassLoader.getSystemClassLoader());
            switch (getAgentMainMethod(agentClass)) {
                case Either.Left(var exception) -> throw exception;
                case Either.Right(var handle) -> {
                    if (handle.type().parameterCount() == 1) handle.invokeExact(agentArgs);
                    else handle.invokeExact(agentArgs, instrumentation);
                }
            }

        } catch (Throwable e) {
            System.err.printf("Failed to run agent: %s(%s)%n", agentPath, agentArgs);
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private Either<Exception, MethodHandle> getAgentMainMethod(Class<?> agentClass) {
        var lookup = MethodHandles.publicLookup();
        var type = MethodType.methodType(void.class, String.class, Instrumentation.class);
        var fallbackType = MethodType.methodType(void.class, String.class);
        var exception = new Exception("Failed to find agent method for " + agentClass);
        try {
            return Either.right(lookup.findStatic(agentClass, "premain", type));
        } catch (Exception e) {
            exception.addSuppressed(e);
        }
        try {
            return Either.right(lookup.findStatic(agentClass, "premain", fallbackType));
        } catch (Exception e) {
            exception.addSuppressed(e);
        }
        try {
            return Either.right(lookup.findStatic(agentClass, "agentmain", type));
        } catch (Exception e) {
            exception.addSuppressed(e);
        }
        try {
            return Either.right(lookup.findStatic(agentClass, "agentmain", fallbackType));
        } catch (Exception e) {
            exception.addSuppressed(e);
        }
        return Either.left(exception);
    }

    private @Nullable String getAgentClassForJar(Manifest manifest) {
        Attributes attributes = manifest.getMainAttributes();
        String premainClass = attributes.getValue("Premain-Class");
        if (premainClass != null) return premainClass;
        return attributes.getValue("Agent-Class");
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

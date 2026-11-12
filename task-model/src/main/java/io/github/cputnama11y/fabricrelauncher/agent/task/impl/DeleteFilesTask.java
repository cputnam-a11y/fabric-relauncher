package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.GlobalConfig;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public record DeleteFilesTask(List<Path> paths) implements Task {
    public static final MapCodec<DeleteFilesTask> CODEC =
            Codec.STRING.xmap(Path::of, Path::toString)
                    .listOrSingle()
                    .fieldOf("paths")
                    .xmap(DeleteFilesTask::new, DeleteFilesTask::paths);

    public DeleteFilesTask {
        paths = List.copyOf(paths);
    }

    public static void delete(Path root, Path path) throws IOException {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(path).normalize();

        if (!target.startsWith(normalizedRoot) || target.equals(normalizedRoot)) {
            throw new IOException("Path must be inside the instance directory: " + path);
        }

        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory does not exist: " + target);
        }

        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                if (exc != null) {
                    throw exc;
                }

                // Preserve the requested root directory.
                if (!dir.equals(target)) {
                    Files.delete(dir);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public void run(Instrumentation instrumentation) {
        Path root = GlobalConfig.getInstance().getRootPath();

        for (Path path : paths) {
            try {
                delete(root, path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to delete directory contents: " + path, e);
            }
        }
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

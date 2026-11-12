package io.github.cputnama11y.fabricrelauncher.agent.task.impl;

import fish.cichlidmc.tinycodecs.api.codec.Codec;
import fish.cichlidmc.tinycodecs.api.codec.CompositeCodec;
import fish.cichlidmc.tinycodecs.api.codec.map.MapCodec;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.GlobalConfig;
import io.github.cputnama11y.fabricrelauncher.agent.task.api.Task;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

public record CopyFilesTask(List<Mapping> mappings) implements Task {
    public static final MapCodec<CopyFilesTask> CODEC = Mapping.CODEC.listOrSingle().optional(List.of()).fieldOf("specs").xmap(
            CopyFilesTask::new,
            CopyFilesTask::mappings
    );

    public record Mapping(Path from, Path to, List<String> include, List<String> exclude, boolean overwrite) {
        public static final Codec<Mapping> CODEC = CompositeCodec.of(
                Codec.STRING.fieldOf("from")
                        .xmap(Path::of, Path::toString),
                Mapping::from,
                Codec.STRING.fieldOf("to")
                        .xmap(Path::of, Path::toString),
                Mapping::to,
                Codec.STRING.listOrSingle().optional(List.of()).fieldOf("include"),
                Mapping::include,
                Codec.STRING.listOrSingle().optional(List.of()).fieldOf("exclude"),
                Mapping::exclude,
                Codec.BOOL.optional(true).fieldOf("overwrite"),
                Mapping::overwrite,
                Mapping::new
        ).codec();

        public void copy(Path root) throws IOException {
            Path normalizedRoot = root.toAbsolutePath().normalize();

            Path source = normalizedRoot.resolve(from).normalize();
            Path destination = normalizedRoot.resolve(to).normalize();

            if (!source.startsWith(normalizedRoot)
                    || !destination.startsWith(normalizedRoot)) {
                throw new IOException("Mapping paths must remain inside the instance directory");
            }

            if (!Files.isDirectory(source)) {
                throw new IOException("Source directory does not exist: " + source);
            }

            FileSystem fs = FileSystems.getDefault();

            List<PathMatcher> includes = include.isEmpty()
                                         ? List.of(fs.getPathMatcher("glob:**"))
                                         : include.stream()
                                                 .map(pattern -> fs.getPathMatcher("glob:" + pattern))
                                                 .toList();

            List<PathMatcher> excludes = exclude.stream()
                    .map(pattern -> fs.getPathMatcher("glob:" + pattern))
                    .toList();

            try (Stream<Path> paths = Files.walk(source)) {
                for (Path path : paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).toList()) {
                    Path relative = source.relativize(path);

                    if (includes.stream().noneMatch(m -> m.matches(relative))) {
                        continue;
                    }

                    if (excludes.stream().anyMatch(m -> m.matches(relative))) {
                        continue;
                    }

                    Path target = destination.resolve(relative);

                    Files.createDirectories(target.getParent());

                    if (overwrite) {
                        Files.copy(path, target,
                                StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        Files.copy(path, target);
                    }
                }
            }
        }
    }

    @Override
    public void run(Instrumentation instrumentation) {
        Path root = GlobalConfig.getInstance().getRootPath();

        for (Mapping mapping : mappings) {
            try {
                mapping.copy(root);
            } catch (IOException e) {
                throw new RuntimeException("Failed to copy files: "
                        + mapping.from + " -> " + mapping.to, e);
            }
        }
    }

    @Override
    public MapCodec<? extends Task> codec() {
        return CODEC;
    }
}

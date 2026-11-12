package io.github.cputnama11y.fabricrelauncher.agent.task.api;

import java.nio.file.Path;

public final class GlobalConfig {
    private static final GlobalConfig INSTANCE = new GlobalConfig();
    private Path root = Path.of("").toAbsolutePath();

    private GlobalConfig() {

    }

    public static GlobalConfig getInstance() {
        return INSTANCE;
    }

    public void setRootPath(Path root) {
        this.root = root;
    }

    public Path getRootPath() {
        return root;
    }
}

package io.github.cputnama11y.modrinthunsuprelaunch.util;

import io.github.cputnama11y.modrinthunsuprelaunch.infoextractor.*;
import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.ExecvRelauncher;
import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.Relauncher;
import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.SubProcessRelauncher;

import java.util.List;

public final class Registry {
    private Registry() {
    }

    private static final List<ProcessInfoExtractor> INFO_EXTRACTORS = List.of(new ProcessHandleInfoExtractor(), new MacProcessInfoExtractor(), new WindowsProcessInfoExtractor(), new ProcfsProcessInfoExtractor());
    private static final List<Relauncher<?>> RELAUNCHERS = List.of(new SubProcessRelauncher(), new ExecvRelauncher());

    public static List<ProcessInfoExtractor> getApplicableProcessInfoExtractors() {
        return INFO_EXTRACTORS;
    }

    public static List<Relauncher<?>> getApplicableRelaunchers() {
        var forcedRelauncher = System.getProperty(Registry.class.getName() + ".force-relauncher");
        if (forcedRelauncher != null) {
            return RELAUNCHERS.stream().filter(it -> it.getClass().getName().equals(forcedRelauncher)).toList();
        }
        return RELAUNCHERS;
    }
}

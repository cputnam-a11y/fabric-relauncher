package io.github.cputnama11y.modrinthunsuprelaunch;

import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.Relauncher;
import io.github.cputnama11y.modrinthunsuprelaunch.relauncher.SubProcessRelauncher;
import io.github.cputnama11y.modrinthunsuprelaunch.util.Cleanup;
import io.github.cputnama11y.modrinthunsuprelaunch.util.MutableObject;
import io.github.cputnama11y.modrinthunsuprelaunch.util.Registry;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;
import net.fabricmc.loader.impl.util.log.Log;
import net.fabricmc.loader.impl.util.log.LogCategory;

import java.io.Closeable;
import java.io.IOException;

public class FabricRelauncher implements LanguageAdapter {
    public static final LogCategory category = LogCategory.createCustom("FabricRelauncher", "RelaunchHijacker");

    static {
        doRelaunch();
    }

    public static void doRelaunch() {
        Cleanup knotCleanup = ctx -> ctx.asClass(SubProcessRelauncher.class).ifPresent(subProcess -> {
            try {
                ((Closeable) FabricLauncherBase.getLauncher().getTargetClassLoader().getParent()).close();
            } catch (IOException _) {
            }
        });
        Log.info(category, "No, I don't think so...");
        Log.info(category, "Let's try again...");
        Exception exception = null;
        for (Relauncher<?> relauncher : Registry.getApplicableRelaunchers()) {
            try {
                Object extracted = relauncher.extract();
                //noinspection unchecked
                ((Relauncher<Object>) relauncher).relaunch(makeParams(extracted, Cleanup.composite(knotCleanup)));
                System.exit(-1);
            } catch (Relauncher.ExtractionFailedException e) {
                if (exception == null) exception = e;
                else exception.addSuppressed(e);
            } catch (Relauncher.RelaunchFailedException e) {
                throw new RuntimeException(e);
            }
        }
        if (exception == null) throw new RuntimeException("Failde to relaunch");
        else throw new RuntimeException(exception);
    }

    @Override
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        throw new LanguageAdapterException("NO!");
    }

    private static <T> Relauncher.RelaunchParameters<T> makeParams(T params, Cleanup cleanup) {
        return new Relauncher.RelaunchParameters<T>() {
            @Override
            public T getLaunchParams() {
                return params;
            }

            @Override
            public Cleanup cleanup() {
                return cleanup;
            }
        };
    }
}

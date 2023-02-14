package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ForgeLoader extends AbstractJarFileModLocator {
    public static final String PLATFORM = "forge";
    public static final URL UNPACKED_LIB = Loader.loadLib(Constants.MOD_NAME, PLATFORM);
    public static final URL UNPACKED_MOD = Loader.loadMod(Constants.MOD_NAME, PLATFORM);

    @Override
    public List<ModFileOrException> scanMods() {
        try {
            return Collections.singletonList(createMod(Paths.get(UNPACKED_MOD.toURI())));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Stream<Path> scanCandidates() {
        return Stream.empty();
    }

    public String name() {
        return "fastmc-mod-loader-" + Constants.MOD_NAME;
    }

    public String toString() {
        return "{fastmc-mod-loader-" + Constants.MOD_NAME + "}";
    }

    public void initArguments(final Map<String, ?> arguments) {}
}

package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.forgespi.locating.IModFile;

import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ForgeLoader extends AbstractJarFileLocator {
    public static final String PLATFORM = "forge";
    public static final URL UNPACKED_LIB = Loader.loadLib(Constants.MOD_NAME, PLATFORM);
    public static final URL UNPACKED_MOD = Loader.loadMod(Constants.MOD_NAME, PLATFORM);

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Override
    public List<IModFile> scanMods() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            InjectClassLoader.hackInModuleClassLoader(cl, UNPACKED_LIB);
            return Collections.singletonList(createMod(Paths.get(UNPACKED_MOD.toURI())).get());
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

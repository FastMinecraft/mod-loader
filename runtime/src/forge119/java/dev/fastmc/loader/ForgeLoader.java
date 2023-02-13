package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ForgeLoader extends AbstractJarFileModLocator {
    private static final String PLATFORM = "forge";

    @Override
    public List<ModFileOrException> scanMods() {
        Path unpacked = Loader.load(Constants.MOD_NAME, PLATFORM).toPath();

        try {
            return Collections.singletonList(createMod(unpacked));
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

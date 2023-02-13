package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ForgeLoader extends AbstractJarFileLocator {
    private static final String PLATFORM = "forge";

    @Override
    public List<IModFile> scanMods() {
        Path unpacked = Loader.load(Constants.MOD_NAME, PLATFORM).toPath();

        try {
            ModFile modFile = ModFile.newFMLInstance(unpacked, this);
            modJars.put(modFile, createFileSystem(modFile));
            return Collections.singletonList(modFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String name() {
        return "fastmc-mod-loader-" + Constants.MOD_NAME;
    }

    public String toString() {
        return "{fastmc-mod-loader-" + Constants.MOD_NAME + "}";
    }

    public void initArguments(final Map<String, ?> arguments) {}
}

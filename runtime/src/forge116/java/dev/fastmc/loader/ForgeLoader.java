package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;

import java.net.URL;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ForgeLoader extends AbstractJarFileLocator {
    public static final String PLATFORM = "forge";
    public static final URL UNPACKED_MOD = Loader.loadMod(Constants.MOD_NAME, PLATFORM);

    @Override
    public List<IModFile> scanMods() {
        try {
            ModFile modFile = ModFile.newFMLInstance(Paths.get(UNPACKED_MOD.toURI()), this);
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

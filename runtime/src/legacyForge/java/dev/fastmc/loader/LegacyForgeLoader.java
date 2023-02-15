package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

public class LegacyForgeLoader implements IFMLLoadingPlugin {
    public static final String PLATFORM = "forge";
    public static final URL UNPACKED_MOD = Loader.loadMod(Constants.MOD_NAME, PLATFORM);

    public LegacyForgeLoader() {
        try {

            Loader.LOGGER.info("Loading mod into classloader");
            LaunchClassLoader launchClassLoader = Launch.classLoader;
            ClassLoader appClassLoader = LaunchClassLoader.class.getClassLoader();

            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(appClassLoader, UNPACKED_MOD);
            addURLMethod.invoke(launchClassLoader, UNPACKED_MOD);

            Loader.LOGGER.debug("Initializing mixin bootstrap");
            Class<?> mixinBootstrap = Class.forName(
                "org.spongepowered.asm.launch.MixinBootstrap",
                true,
                appClassLoader
            );
            Method init = mixinBootstrap.getDeclaredMethod("init");
            init.invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}

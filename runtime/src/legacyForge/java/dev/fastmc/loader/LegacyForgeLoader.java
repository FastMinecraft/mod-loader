package dev.fastmc.loader;

import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Map;

public class LegacyForgeLoader implements IFMLLoadingPlugin {
    private static final String PLATFORM = "forge";

    public LegacyForgeLoader() {
        try {
            File unpacked = Loader.load(Constants.MOD_NAME, PLATFORM);
            URL unpackedURL = unpacked.toURI().toURL();

            Loader.LOGGER.info("Appending class loader");
            LaunchClassLoader launchClassLoader = Launch.classLoader;
            ClassLoader appClassLoader = LaunchClassLoader.class.getClassLoader();

            Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURLMethod.setAccessible(true);
            addURLMethod.invoke(appClassLoader, unpackedURL);
            addURLMethod.invoke(launchClassLoader, unpackedURL);

            Loader.LOGGER.info("Initializing mixin bootstrap");
            Class<?> mixinBootstrap = Class.forName("org.spongepowered.asm.launch.MixinBootstrap", true, appClassLoader);
            Method init = mixinBootstrap.getDeclaredMethod("init");
            init.invoke(null);

//            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins", true, appClassLoader);
//            Method addConfiguration = mixins.getDeclaredMethod("addConfigurations", String[].class);
//            String[] mixinConfigs = Loader.getMixinConfigs(PLATFORM);
//            Loader.LOGGER.info("Loading mixin configs: " + Arrays.toString(mixinConfigs));
//            addConfiguration.invoke(null, (Object) mixinConfigs);
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

package dev.fastmc.loader;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

public class FabricLoader implements PreLaunchEntrypoint {
    private static final String PLATFORM = "fabric";

    @Override
    public void onPreLaunch() {
        try {
            File unpacked = Loader.load(Constants.MOD_NAME, PLATFORM);

            Loader.LOGGER.info("Appending class loader");
            ClassLoader classLoader = this.getClass().getClassLoader();
            Class<?> classLoaderAccess = Class.forName("net.fabricmc.loader.impl.launch.knot.KnotClassDelegate$ClassLoaderAccess");
            Method addUrlFwd = classLoaderAccess.getDeclaredMethod("addUrlFwd", URL.class);
            addUrlFwd.setAccessible(true);
            addUrlFwd.invoke(classLoader, unpacked.toURI().toURL());

            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
            Method addConfiguration = mixins.getDeclaredMethod("addConfigurations", String[].class);
            String[] mixinConfigs = Loader.getMixinConfigs(PLATFORM);
            Loader.LOGGER.info("Loading mixin configs: " + Arrays.toString(mixinConfigs));
            addConfiguration.invoke(null, (Object) mixinConfigs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

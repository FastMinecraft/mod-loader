package dev.fastmc.loader;

import dev.fastmc.loader.core.Constants;
import dev.fastmc.loader.core.Loader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;

public class FabricLoader implements PreLaunchEntrypoint {
    public static final String PLATFORM = "fabric";
    public static final URL UNPACKED_MOD = Loader.loadMod(Constants.MOD_NAME, PLATFORM);

    @Override
    public void onPreLaunch() {
        try {
            Loader.LOGGER.info("Loading mod into classloader");
            ClassLoader classLoader = this.getClass().getClassLoader();
            Class<?> classLoaderAccess = Class.forName(
                "net.fabricmc.loader.impl.launch.knot.KnotClassDelegate$ClassLoaderAccess"
            );
            Method addUrlFwd = classLoaderAccess.getDeclaredMethod("addUrlFwd", URL.class);
            addUrlFwd.setAccessible(true);
            addUrlFwd.invoke(classLoader, UNPACKED_MOD);

            String[] mixinConfigs = Loader.getMixinConfigs(PLATFORM);
            Loader.LOGGER.debug("Loading mixin configs: " + Arrays.toString(mixinConfigs));
            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
            Method addConfiguration = mixins.getDeclaredMethod("addConfigurations", String[].class);
            addConfiguration.invoke(null, (Object) mixinConfigs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

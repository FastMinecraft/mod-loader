package dev.fastmc.loader;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;

public class FabricLoader implements PreLaunchEntrypoint {
    private static final String PLATFORM = "fabric";

    @Override
    public void onPreLaunch() {
        try {
            File unpacked = Loader.load(Constants.MOD_NAME, PLATFORM);

            ClassLoader classLoader = this.getClass().getClassLoader();
            System.out.println(classLoader.getClass().getName());

            Class<?> classLoaderAccess = Class.forName(
                "net.fabricmc.loader.impl.launch.knot.KnotClassDelegate$ClassLoaderAccess");
            Method addUrlFwd = classLoaderAccess.getDeclaredMethod("addUrlFwd", URL.class);
            addUrlFwd.setAccessible(true);
            addUrlFwd.invoke(classLoader, unpacked.toURI().toURL());

            Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
            Method addConfiguration = mixins.getDeclaredMethod("addConfigurations", String[].class);
            String[] mixinConfigs = Loader.getMixinConfigs(PLATFORM);
            addConfiguration.invoke(null, (Object) mixinConfigs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

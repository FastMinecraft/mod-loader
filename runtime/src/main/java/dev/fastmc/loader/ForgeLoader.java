package dev.fastmc.loader;

import cpw.mods.modlauncher.EnumerationHelper;
import cpw.mods.modlauncher.TransformingClassLoader;
import cpw.mods.modlauncher.api.*;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings({ "NullableProblems", "RedundantThrows" })
public class ForgeLoader implements ITransformationService {
    private static final String PLATFORM = "forge";
    private static URLClassLoader CLASS_LOADER;
    private static final File UNPACKED = Loader.load(Constants.MOD_NAME, PLATFORM);

    @Nonnull
    @Override
    public String name() {
        return Constants.MOD_NAME;
    }

    @Override
    public void initialize(IEnvironment environment) {

    }

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }

    @SuppressWarnings("rawtypes")
    @Nonnull
    @Override
    public List<ITransformer> transformers() {
        return Collections.singletonList(new Transformer());
    }

    private static class Transformer implements ITransformer<ClassNode> {
        @SuppressWarnings({ "unchecked", "deprecation" })
        @Nonnull
        @Override
        public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
            Loader.LOGGER.info("Adding access transformer");
            addAt();

            try {
                Loader.LOGGER.info("Appending class loader");
                CLASS_LOADER = new URLClassLoader(new URL[]{ UNPACKED.toURI().toURL() });

                TransformingClassLoader classLoader = (TransformingClassLoader) Thread.currentThread().getContextClassLoader();
                Field resourceFinderField = TransformingClassLoader.class.getDeclaredField("resourceFinder");
                boolean accessible = resourceFinderField.isAccessible();
                resourceFinderField.setAccessible(true);
                Function<String, Enumeration<URL>> resourceFinder =
                    (Function<String, Enumeration<URL>>) resourceFinderField.get(classLoader);
                resourceFinderField.set(
                    classLoader,
                    EnumerationHelper.mergeFunctors(
                        resourceFinder,
                        (path -> LamdbaExceptionUtils.uncheck(() -> CLASS_LOADER.findResources(path)))
                    )
                );
                resourceFinderField.setAccessible(accessible);

                Class<?> mixins = Class.forName("org.spongepowered.asm.mixin.Mixins");
                Method addConfiguration = mixins.getDeclaredMethod("addConfigurations", String[].class);
                String[] mixinConfigs = Loader.getMixinConfigs(PLATFORM);
                Loader.LOGGER.info("Loading mixin configs: " + Arrays.toString(mixinConfigs));
                addConfiguration.invoke(null, (Object) mixinConfigs);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            return input;
        }

        private static void addAt() {
            try {
                File atFileOut = new File(Loader.LOADER_DIR, Loader.getFileName(Constants.MOD_NAME, PLATFORM) + "_at.cfg");
                try (ZipFile zipFile = new ZipFile(UNPACKED)) {
                    ZipEntry entry = zipFile.getEntry("META-INF/accesstransformer.cfg");
                    try (InputStream atIn = zipFile.getInputStream(entry)) {
                        byte[] buffer = new byte[512];
                        try (FileOutputStream atOut = new FileOutputStream(atFileOut)) {
                            for (int length; (length = atIn.read(buffer)) > 0; ) {
                                atOut.write(buffer, 0, length);
                            }
                        }
                    }
                }

                Class<?> fmlLoaderClass = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
                Field accessTransformerField = fmlLoaderClass.getDeclaredField("accessTransformer");
                accessTransformerField.setAccessible(true);
                ILaunchPluginService service = (ILaunchPluginService) Objects.requireNonNull(accessTransformerField.get(null));
                service.offerResource(atFileOut.toPath(), UNPACKED.getName());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Nonnull
        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Nonnull
        @Override
        public Set<Target> targets() {
            return Collections.singleton(Target.targetPreClass("net.minecraft.client.main.Main"));
        }
    }
}

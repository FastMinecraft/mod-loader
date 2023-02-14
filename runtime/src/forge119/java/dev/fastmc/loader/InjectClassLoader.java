package dev.fastmc.loader;

import dev.fastmc.loader.core.Loader;
import dev.fastmc.loader.core.UnsafeUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InjectClassLoader extends URLClassLoader {
    private final ClassLoader target;
    private final Object ucp;
    private final Set<String> packageNames = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public InjectClassLoader(ClassLoader parent) throws Throwable {
        super(new URL[0], parent);
        this.target = parent;
        this.ucp = UCP_GETTER.invoke(this);
        Loader.LOGGER.info("Injecting classloader " + parent.toString());
    }
    @Override
    protected void addURL(URL url) {
        packageNames.addAll(readPackageList(url));
        super.addURL(url);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return loadClass(name, false);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        String packageName = name.substring(0, name.lastIndexOf('.'));
        if (!packageNames.contains(packageName)) {
            return target.loadClass(name);
        }

        synchronized (getClassLoadingLock(name)) {
            Class<?> c = this.findLoadedClass(name);

            if (c == null) {
                try {
                    c = (Class<?>) CLASSLOADER_FIND_LOADED_CLASS.invoke(target, name);
                } catch (Throwable e) {
                    // ignore
                }
            }

            if (c == null) {
                c = findClass(name);
            }

            if (resolve) {
                resolveClass(c);
            }

            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        try {
            String path = name.replace('.', '/').concat(".class");
            Object res = UCP_GET_RESOURCE.invoke(ucp, path, false);
            return defineClass0(name, res);
        } catch (Throwable t) {
            throw new ClassNotFoundException(name, t);
        }
    }

    private Class<?> defineClass0(String name, Object res) throws Throwable {
        int i = name.lastIndexOf('.');
        URL url = (URL) RESOURCE_GET_CODE_SOURCE_URL.invoke(res);

        if (i != -1) {
            String pkgName = name.substring(0, i);
            Manifest man = (Manifest) RESOURCE_GET_MANIFEST.invoke(res);

            if (target.getDefinedPackage(pkgName) == null) {
                try {
                    if (man != null) {
                        definePackage0(pkgName, man, url);
                    } else {
                        definePackage0(pkgName, null, null, null, null, null, null, null);
                    }
                } catch (IllegalArgumentException iae) {
                    if (target.getDefinedPackage(pkgName) == null) {
                        throw new AssertionError("Cannot find package " + pkgName);
                    }
                }
            }
        }

        ByteBuffer bb = (ByteBuffer) RESOURCE_GET_BYTE_BUFFER.invoke(res);
        Class<?> result;
        if (bb != null) {
            result = (Class<?>) CLASSLOADER_DEFINE_CLASS_BYTE_BUFFER.invoke(
                target,
                name,
                bb,
                null
            );
        } else {
            byte[] b = (byte[]) RESOURCE_GET_BYTES.invoke(res);
            result = (Class<?>) CLASSLOADER_DEFINE_CLASS_BYTE_ARRAY.invoke(
                target,
                name,
                b,
                0,
                b.length,
                null
            );
        }
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    private Package definePackage0(String name, Manifest man, URL url) throws Throwable {
        String specTitle = null, specVersion = null, specVendor = null;
        String implTitle = null, implVersion = null, implVendor = null;
        String sealed = null;
        URL sealBase = null;

        Attributes attr = (Attributes) MANIFEST_GET_TRUSTED_ATTRIBUTES.invoke(man, name.replace('.', '/').concat("/"));

        if (attr != null) {
            specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
            specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
            specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
            implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            sealed = attr.getValue(Attributes.Name.SEALED);
        }
        attr = man.getMainAttributes();
        if (attr != null) {
            if (specTitle == null) {
                specTitle = attr.getValue(Attributes.Name.SPECIFICATION_TITLE);
            }
            if (specVersion == null) {
                specVersion = attr.getValue(Attributes.Name.SPECIFICATION_VERSION);
            }
            if (specVendor == null) {
                specVendor = attr.getValue(Attributes.Name.SPECIFICATION_VENDOR);
            }
            if (implTitle == null) {
                implTitle = attr.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
            }
            if (implVersion == null) {
                implVersion = attr.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            }
            if (implVendor == null) {
                implVendor = attr.getValue(Attributes.Name.IMPLEMENTATION_VENDOR);
            }
            if (sealed == null) {
                sealed = attr.getValue(Attributes.Name.SEALED);
            }
        }
        if ("true".equalsIgnoreCase(sealed)) {
            sealBase = url;
        }

        return definePackage0(name, specTitle, specVersion, specVendor, implTitle, implVersion, implVendor, sealBase);
    }

    private Package definePackage0(
        String name,
        String specTitle,
        String specVersion,
        String specVendor,
        String implTitle,
        String implVersion,
        String implVendor,
        URL sealBase
    ) throws Throwable {
        return (Package) CLASSLOADER_DEFINE_PACKAGE.invoke(
            target,
            name,
            specTitle,
            specVersion,
            specVendor,
            implTitle,
            implVersion,
            implVendor,
            sealBase
        );
    }

    @Override
    public void close() {
        // Do nothing
    }


    private static final String UNIQUE_KEY = "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";

    private static final MethodHandle UCP_GETTER;
    private static final MethodHandle UCP_GET_RESOURCE;

    private static final MethodHandle RESOURCE_GET_CODE_SOURCE_URL;
    private static final MethodHandle RESOURCE_GET_MANIFEST;
    private static final MethodHandle RESOURCE_GET_BYTE_BUFFER;
    private static final MethodHandle RESOURCE_GET_BYTES;

    private static final MethodHandle MANIFEST_GET_TRUSTED_ATTRIBUTES;

    private static final MethodHandle CLASSLOADER_FIND_LOADED_CLASS;
    private static final MethodHandle CLASSLOADER_DEFINE_PACKAGE;
    private static final MethodHandle CLASSLOADER_DEFINE_CLASS_BYTE_BUFFER;
    private static final MethodHandle CLASSLOADER_DEFINE_CLASS_BYTE_ARRAY;


    private static ClassLoader injectClassLoader;

    static {
        try {
            Class<?> urlClassPathClass = Class.forName("jdk.internal.loader.URLClassPath");
            Class<?> resourceClass = Class.forName("jdk.internal.loader.Resource");

            UCP_GETTER = UnsafeUtil.TRUSTED_LOOKUP.findGetter(
                URLClassLoader.class,
                "ucp",
                urlClassPathClass
            );

            UCP_GET_RESOURCE = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                urlClassPathClass,
                "getResource",
                MethodType.methodType(resourceClass, String.class, boolean.class)
            );

            RESOURCE_GET_CODE_SOURCE_URL = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                resourceClass,
                "getCodeSourceURL",
                MethodType.methodType(URL.class)
            );

            RESOURCE_GET_MANIFEST = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                resourceClass,
                "getManifest",
                MethodType.methodType(Manifest.class)
            );

            RESOURCE_GET_BYTE_BUFFER = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                resourceClass,
                "getByteBuffer",
                MethodType.methodType(ByteBuffer.class)
            );

            RESOURCE_GET_BYTES = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                resourceClass,
                "getBytes",
                MethodType.methodType(byte[].class)
            );

            MANIFEST_GET_TRUSTED_ATTRIBUTES = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                Manifest.class,
                "getTrustedAttributes",
                MethodType.methodType(Attributes.class, String.class)
            );

            CLASSLOADER_FIND_LOADED_CLASS = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                ClassLoader.class,
                "findLoadedClass",
                MethodType.methodType(Class.class, String.class)
            );

            CLASSLOADER_DEFINE_PACKAGE = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                ClassLoader.class,
                "definePackage",
                MethodType.methodType(
                    Package.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    String.class,
                    URL.class
                )
            );

            CLASSLOADER_DEFINE_CLASS_BYTE_BUFFER = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                ClassLoader.class,
                "defineClass",
                MethodType.methodType(Class.class, String.class, ByteBuffer.class, ProtectionDomain.class)
            );

            CLASSLOADER_DEFINE_CLASS_BYTE_ARRAY = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                ClassLoader.class,
                "defineClass",
                MethodType.methodType(
                    Class.class,
                    String.class,
                    byte[].class,
                    int.class,
                    int.class,
                    ProtectionDomain.class
                )
            );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings({ "unchecked", "SynchronizationOnLocalVariableOrMethodParameter" })
    public static void hackInModuleClassLoader(ClassLoader target, URL url) {
        List<String> packages = readPackageList(url);

        try {
            Class<? extends ClassLoader> moduleClassLoaderClass = target.getClass();
            Map<String, ClassLoader> parentLoaders = (Map<String, ClassLoader>) UnsafeUtil.TRUSTED_LOOKUP.findGetter(
                moduleClassLoaderClass,
                "parentLoaders",
                Map.class
            ).invoke(target);

            if (injectClassLoader == null) {
                synchronized (parentLoaders) {
                    injectClassLoader = parentLoaders.get(UNIQUE_KEY);
                    if (injectClassLoader == null) {
                        injectClassLoader = new InjectClassLoader(target);
                        parentLoaders.put(UNIQUE_KEY, injectClassLoader);
                    }
                    UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                        injectClassLoader.getClass(),
                        "addURL",
                        MethodType.methodType(void.class, URL.class)
                    ).invoke(injectClassLoader, url);
                }
            }

            for (String packageName : packages) {
                parentLoaders.put(packageName, injectClassLoader);
            }

            addResolvedRoot(target, url);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addResolvedRoot(
        ClassLoader target,
        URL url
    ) throws Throwable {
        ((Map<String, Object>) UnsafeUtil.TRUSTED_LOOKUP.findGetter(
            target.getClass(),
            "resolvedRoots",
            Map.class
        ).invoke(target)).put(url.toString(), newJarModuleReference(url));
    }

    private static List<String> readPackageList(URL url) {
        List<String> packages = new ArrayList<>();

        try (ZipFile zipFile = new ZipFile(new File(url.toURI()))) {
            ZipEntry entry = zipFile.getEntry("package-list.txt");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    packages.add(line);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return packages;
    }

    private static Object newJarModuleReference(URL url) {
        try {
            Class<?> jarModuleReferenceClass = Class.forName("cpw.mods.cl.JarModuleFinder$JarModuleReference");
            Class<?> secureJarClass = Class.forName("cpw.mods.jarhandling.SecureJar");
            Class<?> moduleDataProviderClass = Class.forName("cpw.mods.jarhandling.SecureJar$ModuleDataProvider");

            Object jar = UnsafeUtil.TRUSTED_LOOKUP.findStatic(
                secureJarClass,
                "from",
                MethodType.methodType(secureJarClass, Path[].class)
            ).invoke(Paths.get(url.toURI()));

            Object moduleDataProvider = UnsafeUtil.TRUSTED_LOOKUP.findVirtual(
                secureJarClass,
                "moduleDataProvider",
                MethodType.methodType(moduleDataProviderClass)
            ).invoke(jar);

            return UnsafeUtil.TRUSTED_LOOKUP.findConstructor(
                jarModuleReferenceClass,
                MethodType.methodType(void.class, moduleDataProviderClass)
            ).invoke(moduleDataProvider);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}

package dev.fastmc.loader.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.tukaani.xz.XZInputStream;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Loader {
    public static final Logger LOGGER = LogManager.getLogger();
    public static final File LOADER_DIR = new File("fastmc-loader");

    public static String getFileName(String modName, String platform) {
        return modName + "-" + platform;
    }

    public static String[] getMixinConfigs(String platform) {
        List<String> mixins = new ArrayList<>();
        for (String line : Constants.MIXIN_CONFIGS.split(",")) {
            if (line.startsWith(platform + ":")) {
                mixins.add(line.substring(platform.length() + 1));
            }
        }
        return mixins.toArray(new String[0]);
    }

    public static URL loadLib(String modName, String platform) {
        return load(modName, platform + "-libs");
    }

    public static URL loadMod(String modName, String platform) {
        return load(modName, platform);
    }

    private static URL load(String modName, String classifier) {
        File file = loadFile(modName, classifier);
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File loadFile(String modName, String classifier) {
        LOADER_DIR.mkdirs();
        String fileName = modName + "-" + classifier;
        File jarFile = new File(LOADER_DIR, fileName + ".jar");
        File checksumFile = new File(LOADER_DIR, fileName + ".sha512");
        String cachedCheckSum = null;
        if (jarFile.exists() && checksumFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(checksumFile))) {
                cachedCheckSum = reader.readLine();
            } catch (IOException e) {
                // ignored
            }
        }

        byte[] bytes;
        String checksum;
        try (InputStream is = Objects.requireNonNull(getModPackageStream(modName))) {
            bytes = readBytes(is);
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            checksum = toHexString(md.digest(bytes));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Checksum: " + checksum);

        if (checksum.equals(cachedCheckSum)) {
            LOGGER.info("Using cached " + fileName + ".jar");
            return jarFile;
        }

        try (ZipOutputStream zipOut = getZipOut(jarFile)) {
            zipOut.setMethod(ZipOutputStream.DEFLATED);
            zipOut.setLevel(Deflater.BEST_COMPRESSION);
            try (ZipInputStream tarIn = getZipIn(bytes)) {
                ZipEntry entryIn;
                String pathPrefix = classifier + "/";
                byte[] buffer = new byte[1024];
                while ((entryIn = tarIn.getNextEntry()) != null) {
                    if (entryIn.getName().startsWith(pathPrefix)) {
                        ZipEntry entryOut = new ZipEntry(entryIn.getName().substring(pathPrefix.length()));
                        zipOut.putNextEntry(entryOut);
                        for (int len; (len = tarIn.read(buffer)) > 0; ) {
                            zipOut.write(buffer, 0, len);
                        }
                        zipOut.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Writer writer = new FileWriter(checksumFile)) {
            writer.write(checksum);
        } catch (IOException e) {
            // ignored
        }

        return jarFile;
    }

    private static InputStream getModPackageStream(String modName) {
        return Loader.class.getClassLoader().getResourceAsStream(modName + ".zip.xz");
    }

    private static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static ZipInputStream getZipIn(byte[] bytes) throws IOException {
        return new ZipInputStream(new XZInputStream(new ByteArrayInputStream(bytes)));
    }

    private static ZipOutputStream getZipOut(File file) throws IOException {
        return new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(file.toPath())));
    }

    public static byte[] readBytes(InputStream stream) throws IOException {
        byte[] buffer = new byte[Math.max(stream.available(), 1024 * 1024)];
        int read = 0;
        for (int len; (len = stream.read(buffer, read, buffer.length - read)) > 0; ) {
            read += len;
            if (buffer.length - read == 0) {
                buffer = Arrays.copyOf(buffer, buffer.length + buffer.length >> 1);
            }
        }
        return Arrays.copyOf(buffer, read);
    }

    public static Path getSelfJarPath() throws URISyntaxException {
        return Paths.get(getSelfJarUrl().toURI());
    }

    public static URL getSelfJarUrl() {
        return Loader.class.getProtectionDomain().getCodeSource().getLocation();
    }
}

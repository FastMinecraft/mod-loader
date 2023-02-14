package dev.fastmc.loader

import com.google.gson.*
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import java.io.File
import java.util.jar.Attributes
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class GenerateConstantsTask : DefaultTask() {
    @get:Input
    internal abstract val modName: Property<String>

    @get:Input
    internal abstract val modPackage: Property<String>

    @get:Optional
    @get:Input
    internal abstract val forgeModClass: Property<String>

    @get:Optional
    @get:Input
    internal abstract val defaultPlatform: Property<ModPlatform>

    @get:InputFiles
    internal abstract val platformJars: Property<FileCollection>

    @get:Inject
    internal abstract val project: Project

    @get:OutputDirectory
    internal abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    internal abstract val resourcesDir: DirectoryProperty

    @get:OutputFile
    internal abstract val manifestFile: RegularFileProperty

    init {
        sourcesDir.set(project.layout.buildDirectory.dir("mod-loader/sources"))
        resourcesDir.set(project.layout.buildDirectory.dir("mod-loader/resources"))
        manifestFile.set(resourcesDir.file("META-INF/MANIFEST.MF"))
    }

    private val constantsSrc = """
        package %s;

        public class Constants {
            public static String MOD_NAME = "%s";
            public static String MIXIN_CONFIGS = "%s";
        }
    """.trimIndent()

    @TaskAction
    fun run() {
        val sourceDir = sourcesDir.get().asFile
        sourceDir.deleteRecursively()
        sourceDir.mkdirs()

        val resourceDir = resourcesDir.get().asFile
        resourceDir.deleteRecursively()
        resourceDir.mkdirs()

        File(resourceDir, "META-INF").mkdirs()

        val mixinConfigs = mutableListOf<String>()

        val manifestFile = manifestFile.get().asFile
        manifestFile.parentFile.mkdirs()
        manifestFile.createNewFile()

        generateResources(mixinConfigs)
        generateClasses(mixinConfigs)
    }

    private fun generateClasses(mixinConfigs: List<String>) {
        val dir = File(File(sourcesDir.asFile.get(), modPackage.get().replace('.', '/')), "core")
        dir.mkdirs()
        File(dir, "Constants.java").writeText(
            constantsSrc.format(
                "${modPackage.get()}.core",
                modName.get(),
                mixinConfigs.joinToString(",")
            )
        )
    }

    private fun generateResources(mixinConfigs: MutableList<String>) {
        val resourcesDir = resourcesDir.asFile.get()
        resourcesDir.mkdirs()

        defaultPlatform.orNull?.let {
            val file = platformJars.get().singleFile
            when (it) {
                ModPlatform.FABRIC -> fabric(file, mixinConfigs, resourcesDir)
                ModPlatform.FORGE -> forge(file, mixinConfigs, resourcesDir)
            }
        } ?: run {
            platformJars.get().files.find {
                it.name.contains(ModPlatform.FABRIC.id)
            }?.let { file ->
                fabric(file, mixinConfigs, resourcesDir)
            }

            platformJars.get().files.find {
                it.name.contains(ModPlatform.FORGE.id)
            }?.let { file ->
                forge(file, mixinConfigs, resourcesDir)
            }
        }
    }

    private fun fabric(file: File, mixinConfigs: MutableList<String>, resourcesDir: File) {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val zipTree = project.zipTree(file)

        zipTree.findByName("fabric.mod.json")?.let { fabricModJson ->
            val json = JsonParser.parseString(fabricModJson.readText()).asJsonObject

            val entrypoints = json.getAsJsonObject("entrypoints")
                ?: JsonObject().apply { json.add("entrypoints", this) }
            entrypoints.add("preLaunch", JsonArray().apply {
                add("${modPackage.get()}.FabricLoader")
            })

            json.getAsJsonArray("mixins").forEach {
                mixinConfigs.add("fabric:${it.asString}")
            }

            json.remove("mixins")

            json.get("accessWidener")?.asString?.let { accessWidener ->
                zipTree.findByName(accessWidener)?.copyTo(File(resourcesDir, accessWidener), true)
            }

            File(resourcesDir, "fabric.mod.json").bufferedWriter().use {
                gson.toJson(json, it)
            }
        }
    }

    private fun forge(file: File, mixinConfigs: MutableList<String>, resourcesDir: File) {
        val zipTree = project.zipTree(file)
        val newManifest = Manifest()
        newManifest.mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        newManifest.mainAttributes[Attributes.Name("FMLCorePlugin")] = "${modPackage.get()}.LegacyForgeLoader"

        zipTree.findByName("MANIFEST.MF")?.let { oldManifestFile ->
            val manifest = Manifest(oldManifestFile.inputStream())

            manifest.mainAttributes.getValue("MixinConfigs")
                ?.split(',')?.mapTo(mixinConfigs) { "forge:$it" }

            manifest.mainAttributes.getValue("FMLAT")?.let { atName ->
                val atFile = zipTree.findByName(atName)
                    ?: throw IllegalStateException("Could not find access transformer file $atName")
                atFile.copyTo(File(resourcesDir, "META-INF/$atName"), true)

                newManifest.mainAttributes[Attributes.Name("FMLAT")] = atName
            }
        }

        forgeModClass.orNull?.let { forgeModClass ->
            newManifest.mainAttributes[Attributes.Name("FMLCorePluginContainsFMLMod")] = "true"
            zipTree.findByName("mcmod.info")?.copyTo(File(resourcesDir, "mcmod.info"), true)
            zipTree.findByName("pack.mcmeta")?.copyTo(File(resourcesDir, "pack.mcmeta"), true)
            zipTree.findByName("mods.toml")?.copyTo(File(resourcesDir, "META-INF/mods.toml"), true)
            val forgeModClassRegex = "$forgeModClass.*\\.class".toRegex()
            val forgeModPackage = forgeModClass.substringBeforeLast('.').replace('.', '/')
            zipTree
                .filter { it.path.contains(forgeModClassRegex) }
                .forEach {
                    it.copyTo(File(resourcesDir, "$forgeModPackage/${it.name}"), true)
                }
        }

        manifestFile.get().asFile.outputStream().use { newManifest.write(it) }
    }

    private fun Iterable<File>.findByName(name: String): File? {
        return find { it.name == name }
    }
}
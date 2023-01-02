package dev.fastmc.loader

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.jar.Manifest
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class GenerateConstantsTask : DefaultTask() {
    @get:Input
    internal abstract val modName: Property<String>

    @get:Input
    internal abstract val modPackage: Property<String>

    @get:Input
    internal abstract val platforms: Property<Configuration>

    @get:Inject
    internal abstract val project: Project

    @get:OutputDirectory
    internal abstract val sourcesDir: DirectoryProperty

    @get:OutputDirectory
    internal abstract val resourcesDir: DirectoryProperty

    init {
        sourcesDir.set(project.layout.buildDirectory.dir("mod-loader/sources"))
        resourcesDir.set(project.layout.buildDirectory.dir("mod-loader/resources"))
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
        val mixinConfigs = mutableListOf<String>()
        generateResources(mixinConfigs)
        generateClasses(mixinConfigs)
    }

    private fun generateClasses(mixinConfigs: List<String>) {
        val dir = File(sourcesDir.asFile.get(), modPackage.get().replace('.', '/'))
        dir.mkdirs()
        File(dir, "Constants.java").writeText(
            constantsSrc.format(
                modPackage.get(),
                modName.get(),
                mixinConfigs.joinToString(",")
            )
        )
    }

    private fun generateResources(mixinConfigs: MutableList<String>) {
        val resourcesDir = resourcesDir.asFile.get()
        resourcesDir.mkdirs()
        val servicesDir = File(resourcesDir, "META-INF/services")
        servicesDir.mkdirs()
        File(servicesDir, "cpw.mods.modlauncher.api.ITransformationService").writeText(
            "${modPackage.get()}.ForgeLoader"
        )

        val gson = GsonBuilder().setPrettyPrinting().create()

        platforms.get().files.find {
            it.name.contains(ModPlatform.FABRIC.id)
        }?.let { file ->
            val zipTree = project.zipTree(file)

            zipTree.find { it.name == "fabric.mod.json" }?.let { fabricModJson ->
                val json = JsonParser.parseString(fabricModJson.readText()).asJsonObject

                json.add("entrypoints", JsonObject().apply {
                    add("preLaunch", JsonArray().apply {
                        add("${modPackage.get()}.FabricLoader")
                    })
                })

                json.getAsJsonArray("mixins").forEach {
                    mixinConfigs.add("fabric:${it.asString}")
                }

                json.remove("mixins")

                json.get("accessWidener")?.asString?.let { accessWidener ->
                    zipTree.find {
                        it.name == accessWidener
                    }?.copyTo(File(resourcesDir, accessWidener), true)
                }

                File(resourcesDir, "fabric.mod.json").bufferedWriter().use {
                    gson.toJson(json, it)
                }
            }
        }

        platforms.get().files.find {
            it.name.contains(ModPlatform.FORGE.id)
        }?.let { file ->
            val zipTree = project.zipTree(file)

            zipTree.find { it.name == "MANIFEST.MF" }?.let { manifestFile ->
                val manifest = Manifest(manifestFile.inputStream())
                val mixinConfigsString = manifest.mainAttributes.getValue("MixinConfigs")
                mixinConfigsString?.split(',')?.mapTo(mixinConfigs) { "forge:$it" }
            }
        }
    }
}
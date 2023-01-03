package dev.fastmc.loader

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class ModLoaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val platforms = project.configurations.create("modLoaderPlatforms")
        val runtimeConfiguration = project.configurations.create("modLoaderRuntime")
        val extension = project.extensions.create("modLoader", ModLoaderExtension::class.java)

        project.dependencies.add(runtimeConfiguration.name, "dev.fastmc:mod-loader-runtime:$version")

        val generateConstants = project.tasks.create("generateConstants", GenerateConstantsTask::class.java) { generateConstants ->
            generateConstants.modName.set(extension.modName)
            generateConstants.modPackage.set(extension.modPackage)
            generateConstants.platforms.set(platforms)
        }

        val compileConstants = project.tasks.create("compileConstants", JavaCompile::class.java) { compileConstants ->
            compileConstants.destinationDirectory.set(project.layout.buildDirectory.dir("classes/mod-loader"))
            compileConstants.classpath = project.files()
            compileConstants.source(generateConstants.sourcesDir)
        }

        val modPackaging = project.tasks.create("modPackaging", ModPackagingTask::class.java) { modPackaging ->
            modPackaging.modName.set(extension.modName)
            modPackaging.platforms.set(platforms)
        }

        val remapRuntimeTask = project.tasks.create("remapRuntime", RemapRuntimeTask::class.java) { remapRuntime ->
            remapRuntime.modPackage.set(extension.modPackage)
            remapRuntime.runtimeConfiguration.set(runtimeConfiguration)
        }

        val modLoaderJar = project.tasks.create("modLoaderJar", Jar::class.java) { modLoaderJar ->
            modLoaderJar.dependsOn(generateConstants)
            modLoaderJar.from(compileConstants.outputs)
            modLoaderJar.from(remapRuntimeTask.outputs)
            modLoaderJar.from(generateConstants.resourcesDir)
            modLoaderJar.from(modPackaging.outputs)

            modLoaderJar.manifest.attributes(mapOf("FMLCorePlugin" to extension.modPackage.map { "$it.LegacyForgeLoader" }))
        }

        project.artifacts {
            it.add("archives", modLoaderJar)
        }
    }

    companion object {
        val version = ModLoaderPlugin::class.java.getResource("/mod-loader-plugin.version")!!.readText()
    }
}
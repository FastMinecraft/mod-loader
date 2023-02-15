package dev.fastmc.loader

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class ModLoaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val runtimeConfiguration = project.configurations.create("modLoaderRuntime")
        val extension = project.extensions.create("modLoader", ModLoaderExtension::class.java)

        val platforms = project.configurations.register("modLoaderPlatforms")
        val platformJarFiles = platforms.map { project.files(it.files, it.allArtifacts.files.files) }

        project.dependencies.add(runtimeConfiguration.name, "dev.fastmc:mod-loader-runtime:$version")

        project.afterEvaluate {
            val mcVersion = extension.mcVersion.get()
            val ver = mcVersion.split('.')[1].toIntOrNull() ?: 0
            if (ver >= 13) {
                project.dependencies.add(runtimeConfiguration.name, "dev.fastmc:mod-loader-runtime:$version:fabric")
                when (ver) {
                    in 13..17 -> project.dependencies.add(
                        runtimeConfiguration.name,
                        "dev.fastmc:mod-loader-runtime:$version:forge-1.16"
                    )
                    18 -> project.dependencies.add(
                        runtimeConfiguration.name,
                        "dev.fastmc:mod-loader-runtime:$version:forge-1.18"
                    )
                    19 -> project.dependencies.add(
                        runtimeConfiguration.name,
                        "dev.fastmc:mod-loader-runtime:$version:forge-1.19"
                    )
                    else -> throw IllegalStateException("Unsupported Minecraft version: $mcVersion")
                }
            } else {
                project.dependencies.add(
                    runtimeConfiguration.name,
                    "dev.fastmc:mod-loader-runtime:$version:legacy-forge"
                )
            }
        }

        val generateConstants =
            project.tasks.create("generateConstants", GenerateConstantsTask::class.java) { generateConstants ->
                generateConstants.dependsOn(platforms)
                generateConstants.dependsOn(platforms.get().artifacts)

                generateConstants.modName.set(extension.modName)
                generateConstants.modPackage.set(extension.modPackage.map { "${it}Loader" })
                generateConstants.forgeModClass.set(extension.forgeModClass)
                generateConstants.defaultPlatform.set(extension.defaultPlatform)
                generateConstants.platformJars.set(platformJarFiles)
            }

        val compileConstants = project.tasks.create("compileConstants", JavaCompile::class.java) { compileConstants ->
            compileConstants.classpath = project.files()
            compileConstants.source(generateConstants.sourcesDir)
            compileConstants.destinationDirectory.set(project.layout.buildDirectory.dir("classes/mod-loader"))

            @Suppress("ObjectLiteralToLambda")
            compileConstants.actions.add(0, object : Action<Task> {
                override fun execute(t: Task) {
                    val file = compileConstants.destinationDirectory.asFile.get()
                    file.deleteRecursively()
                    file.mkdirs()
                }
            })
        }

        val modPackaging = project.tasks.create("modPackaging", ModPackagingTask::class.java) { modPackaging ->
            modPackaging.dependsOn(platforms)
            modPackaging.dependsOn(platforms.get().artifacts)

            modPackaging.modName.set(extension.modName)
            modPackaging.modPackage.set(extension.modPackage)
            modPackaging.forgeModClass.set(extension.forgeModClass)

            modPackaging.splitLibs.add(extension.mcVersion.map {
                if ((it.split('.')[1].toIntOrNull() ?: 0) >= 18) "forge" else ""
            })
            modPackaging.defaultPlatform.set(extension.defaultPlatform)
            modPackaging.platformJars.set(platformJarFiles)
        }

        val remapRuntimeTask = project.tasks.create("remapRuntime", RemapRuntimeTask::class.java) { remapRuntime ->
            remapRuntime.modPackage.set(extension.modPackage.map { "${it}Loader" })
            remapRuntime.runtimeJars.setFrom(runtimeConfiguration)
        }

        val modLoaderJar = project.tasks.create("modLoaderJar", Jar::class.java) { modLoaderJar ->
            modLoaderJar.dependsOn(generateConstants)
            modLoaderJar.from(compileConstants.destinationDirectory)
            modLoaderJar.from(remapRuntimeTask.outputs)
            modLoaderJar.from(generateConstants.resourcesDir) {
                it.exclude("MANIFEST.MF")
            }
            modLoaderJar.from(modPackaging.outputs)

            modLoaderJar.manifest.from(generateConstants.manifestFile)
        }

        project.artifacts {
            it.add("archives", modLoaderJar)
        }
    }

    companion object {
        val version = ModLoaderPlugin::class.java.getResource("/mod-loader-plugin.version")!!.readText()
    }
}
package dev.fastmc.loader

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

class ModLoaderPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val platforms = project.configurations.create("modLoaderPlatforms")
        val runtimeConfiguration = project.configurations.create("modLoaderRuntime")
        val extension = project.extensions.create("modLoader", ModLoaderExtension::class.java)

        platforms.buildDependencies
        val platformFiles = project.provider { platforms.fileCollection() + platforms.allArtifacts.files }

        project.dependencies.add(runtimeConfiguration.name, "dev.fastmc:mod-loader-runtime:$version")

        val generateConstants =
            project.tasks.create("generateConstants", GenerateConstantsTask::class.java) { generateConstants ->
                generateConstants.modName.set(extension.modName)
                generateConstants.modPackage.set(extension.modPackage)
                generateConstants.defaultPlatform.set(extension.defaultPlatform)
                generateConstants.platformJars.set(platformFiles)
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
            modPackaging.modName.set(extension.modName)
            modPackaging.defaultPlatform.set(extension.defaultPlatform)
            modPackaging.platformsJars.set(platformFiles)
        }

        val remapRuntimeTask = project.tasks.create("remapRuntime", RemapRuntimeTask::class.java) { remapRuntime ->
            remapRuntime.modPackage.set(extension.modPackage)
            remapRuntime.runtimeJar.set(project.provider { runtimeConfiguration.singleFile })
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
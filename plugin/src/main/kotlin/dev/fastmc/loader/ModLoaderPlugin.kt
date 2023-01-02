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

        project.tasks.named("compileJava", JavaCompile::class.java).configure { javaCompile ->
            javaCompile.source(generateConstants.sourcesDir)
        }

        val modPackaging = project.tasks.create("modPackaging", ModPackagingTask::class.java) { modPackaging ->
            modPackaging.modName.set(extension.modName)
            modPackaging.platforms.set(platforms)
        }

        val remapRuntimeTask = project.tasks.create("remapRuntime", RemapRuntimeTask::class.java) { remapRuntime ->
            remapRuntime.modPackage.set(extension.modPackage)
            remapRuntime.runtimeConfiguration.set(runtimeConfiguration)
        }

        project.tasks.named("jar", Jar::class.java).configure { jar ->
            jar.dependsOn(generateConstants)
            jar.from(remapRuntimeTask.outputs)
            jar.from(generateConstants.resourcesDir)
            jar.from(modPackaging.outputs)
        }
    }

    companion object {
        val version = ModLoaderPlugin::class.java.getResource("/mod-loader-plugin.version")!!.readText()
    }
}
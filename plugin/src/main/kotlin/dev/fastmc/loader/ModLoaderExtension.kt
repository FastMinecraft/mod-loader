package dev.fastmc.loader

import org.gradle.api.Project
import org.gradle.api.provider.Property
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class ModLoaderExtension {
    abstract val modName: Property<String>
    abstract val modPackage: Property<String>
    abstract val forgeModClass: Property<String>
    abstract val defaultPlatform: Property<ModPlatform>
    abstract val mcVersion: Property<String>

    @get:Inject
    internal abstract val project: Project

    init {
        modName.convention(project.rootProject.name)
        mcVersion.convention("1.12.2")
    }
}
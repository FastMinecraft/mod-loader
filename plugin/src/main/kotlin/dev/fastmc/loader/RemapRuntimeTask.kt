package dev.fastmc.loader

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject

@Suppress("LeakingThis")
abstract class RemapRuntimeTask : DefaultTask() {
    @get:Input
    internal abstract val modPackage: Property<String>

    @get:InputFiles
    internal abstract val runtimeJars: ConfigurableFileCollection

    @get:OutputDirectory
    internal abstract val outputDirectory: DirectoryProperty

    @get:Inject
    internal abstract val project: Project

    init {
        outputDirectory.set(project.layout.buildDirectory.dir("mod-loader/runtime"))
    }

    @TaskAction
    fun remap() {
        val output = outputDirectory.get().asFile
        output.deleteRecursively()
        output.mkdirs()

        val packageRegex = "dev.fastmc.loader".toRegex()
        val newPackage = modPackage.get().replace('.', '/')

        val remapper = object : Remapper() {
            override fun map(internalName: String): String {
                return internalName.replace(packageRegex, newPackage)
            }
        }

        runtimeJars.forEach {
            ZipInputStream(it.inputStream().buffered(16 * 1024)).use { zipIn ->
                while (true) {
                    val entry = zipIn.nextEntry ?: break
                    val fileTo = File(output, entry.name.replace(packageRegex, newPackage))
                    when {
                        entry.isDirectory -> {
                            // Ignored
                        }
                        entry.name.endsWith(".class") -> {
                            val bytes = zipIn.readBytes()
                            val classReader = ClassReader(bytes)
                            val inputNode = ClassNode()
                            classReader.accept(inputNode, 0)

                            val outputNode = ClassNode()
                            val classRemapper = ClassRemapper(outputNode, remapper)
                            inputNode.accept(classRemapper)

                            val classWriter = ClassWriter(classReader, 0)
                            outputNode.accept(classWriter)
                            fileTo.parentFile.mkdirs()
                            fileTo.writeBytes(classWriter.toByteArray())
                        }
                        else -> {
                            fileTo.parentFile.mkdirs()
                            zipIn.copyTo(fileTo.outputStream())
                        }
                    }
                }
            }
        }
    }
}
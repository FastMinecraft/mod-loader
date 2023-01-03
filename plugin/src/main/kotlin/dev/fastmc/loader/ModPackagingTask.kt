@file:Suppress("LeakingThis")

package dev.fastmc.loader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class ModPackagingTask : DefaultTask() {
    @get:Input
    internal abstract val modName: Property<String>

    @get:Optional
    @get:Input
    internal abstract val defaultPlatform: Property<ModPlatform>

    @get:InputFiles
    internal abstract val platformJars: Property<FileCollection>

    @get:OutputFile
    internal abstract val outputFile: RegularFileProperty

    init {
        outputFile.set(modName.map { project.layout.buildDirectory.file("mod-loader/packed/${it}.xz").get() })
    }

    @TaskAction
    fun packageMod() {
        val outputFile = outputFile.get().asFile
        outputFile.parentFile.mkdirs()
        val rawStream = outputFile.outputStream().buffered(16 * 1024)
        ZipOutputStream(XZOutputStream(rawStream, LZMA2Options(LZMA2Options.PRESET_MAX))).use { zipOut ->
            runBlocking {
                zipOut.setMethod(ZipOutputStream.DEFLATED)
                zipOut.setLevel(0)
                defaultPlatform.orNull?.let {
                    pack(this, platformJars.get().singleFile, it.id, zipOut)
                } ?: run {
                    platformJars.get().forEach { file ->
                        val platform = ModPlatform.values().find { file.name.contains(it.id) } ?: return@forEach
                        pack(this, file, platform.id, zipOut)
                    }
                }
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun pack(scope: CoroutineScope, input: File, name: String, zipOut: ZipOutputStream) {
        scope.launch(Dispatchers.IO) {
            ZipInputStream(input.inputStream().buffered(16 * 1024)).use {
                while (true) {
                    val entryIn = it.nextEntry ?: break
                    val entryOut = ZipEntry("$name/${entryIn.name}")
                    if (!entryIn.isDirectory) {
                        val bytes = it.readBytes()

                        scope.launch {
                            entryOut.size = bytes.size.toLong()
                            zipOut.putNextEntry(entryOut)
                            zipOut.write(bytes)
                        }
                    } else {
                        scope.launch {
                            zipOut.putNextEntry(entryOut)
                        }
                    }
                }
            }
        }
    }
}
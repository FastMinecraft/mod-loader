@file:Suppress("LeakingThis")

package dev.fastmc.loader

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.CRC32

abstract class ModPackagingTask : DefaultTask() {
    @get:Input
    internal abstract val modName: Property<String>

    @get:Optional
    @get:Input
    internal abstract val forgeModClass: Property<String>

    @get:Optional
    @get:Input
    internal abstract val defaultPlatform: Property<ModPlatform>

    @get:InputFiles
    internal abstract val platformJars: Property<FileCollection>

    @get:OutputFile
    internal abstract val outputFile: RegularFileProperty

    init {
        outputFile.set(modName.map { project.layout.buildDirectory.file("mod-loader/packed/${it}.zip.xz").get() })
    }

    @TaskAction
    fun packageMod() {
        val outputFile = outputFile.get().asFile
        outputFile.parentFile.mkdirs()

        val bytes = readToZipBytes()
        System.gc()

        val rawStream = outputFile.outputStream().buffered(16 * 1024)
        val lzma2Options = LZMA2Options(LZMA2Options.PRESET_MAX)
        lzma2Options.dictSize = 16 * 1024 * 1024
        lzma2Options.niceLen = 273
        XZOutputStream(rawStream, lzma2Options).use { it.write(bytes) }
        System.gc()
    }

    private fun readToZipBytes(): ByteArray {
        val byteArrayOut = ByteArrayOutputStream(4 * 1024 * 1024)
        ZipArchiveOutputStream(byteArrayOut).use { zipOut ->
            runBlocking {
                zipOut.setMethod(ZipArchiveOutputStream.STORED)
                val channel = Channel<Pair<ZipArchiveEntry, ByteArray>>(Channel.BUFFERED)

                launch(Dispatchers.IO) {
                    for (entry in channel) {
                        zipOut.putArchiveEntry(entry.first)
                        zipOut.write(entry.second)
                        zipOut.closeArchiveEntry()
                    }
                }

                coroutineScope {
                    defaultPlatform.orNull?.let {
                        pack(platformJars.get().singleFile, it.id, channel)
                    } ?: run {
                        platformJars.get().forEach { file ->
                            val platform = ModPlatform.values().find { file.name.contains(it.id) } ?: return@forEach
                            pack(file, platform.id, channel)
                        }
                    }
                }

                channel.close()
            }
        }
        return byteArrayOut.toByteArray()
    }

    private fun CoroutineScope.pack(
        input: File,
        name: String,
        channel: Channel<Pair<ZipArchiveEntry, ByteArray>>
    ) {
        launch(Dispatchers.IO) {
            val filterName = forgeModClass.map { "${it.replace('.', '/')}.*\\.class".toRegex() }.orNull
            val crc32 = CRC32()
            ZipArchiveInputStream(input.inputStream().buffered(16 * 1024)).use {
                while (true) {
                    val entryIn = it.nextEntry ?: break
                    if (filterName != null && filterName.matches(entryIn.name)) continue
                    val entryOut = ZipArchiveEntry("$name/${entryIn.name}")
                    if (!entryIn.isDirectory) {
                        val bytes = it.readBytes()
                        crc32.reset()
                        crc32.update(bytes)
                        entryOut.crc = crc32.value
                        entryOut.size = bytes.size.toLong()
                        channel.send(entryOut to bytes)
                    }
                }
            }
        }
    }
}
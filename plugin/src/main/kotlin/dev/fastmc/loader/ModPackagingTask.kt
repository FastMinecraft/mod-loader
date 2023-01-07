@file:Suppress("LeakingThis")

package dev.fastmc.loader

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.io.File

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
        outputFile.set(modName.map { project.layout.buildDirectory.file("mod-loader/packed/${it}.tar.xz").get() })
    }

    @TaskAction
    fun packageMod() {
        val outputFile = outputFile.get().asFile
        outputFile.parentFile.mkdirs()

        val bytes = readToTarBytes()
        System.gc()

        val rawStream = outputFile.outputStream().buffered(16 * 1024)
        val lzma2Options = LZMA2Options(LZMA2Options.PRESET_MAX)
        lzma2Options.dictSize = 16 * 1024 * 1024
        lzma2Options.niceLen = 273
        XZOutputStream(rawStream, lzma2Options).use { it.write(bytes) }
        System.gc()
    }

    private fun readToTarBytes(): ByteArray {
        val byteArrayOut = ByteArrayOutputStream(4 * 1024 * 1024)
        TarArchiveOutputStream(byteArrayOut).use { tarOut ->
            runBlocking {
                tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
                val channel = Channel<Pair<TarArchiveEntry, ByteArray?>>(Channel.BUFFERED)

                launch(Dispatchers.IO) {
                    for (entry in channel) {
                        tarOut.putArchiveEntry(entry.first)
                        entry.second?.let { tarOut.write(it) }
                        tarOut.closeArchiveEntry()
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
        channel: SendChannel<Pair<TarArchiveEntry, ByteArray?>>
    ) {
        val filterName = forgeModClass.map { "${it.replace('.', '/')}.*\\.class".toRegex() }.orNull
        launch(Dispatchers.IO) {
            ZipArchiveInputStream(input.inputStream().buffered(16 * 1024)).use {
                while (true) {
                    val entryIn = it.nextEntry ?: break
                    if (filterName != null && filterName.matches(entryIn.name)) continue
                    val entryOut = TarArchiveEntry("$name/${entryIn.name}")
                    if (entryIn.isDirectory) {
                        channel.send(entryOut to null)
                    } else {
                        val bytes = it.readBytes()
                        entryOut.size = bytes.size.toLong()
                        channel.send(entryOut to bytes)
                    }
                }
            }
        }
    }
}
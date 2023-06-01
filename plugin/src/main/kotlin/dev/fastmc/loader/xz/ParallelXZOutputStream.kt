package dev.fastmc.loader.xz

import dev.fastmc.loader.xz.check.Check
import dev.fastmc.loader.xz.common.EncoderUtil
import dev.fastmc.loader.xz.common.StreamFlags
import dev.fastmc.loader.xz.index.IndexEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.OutputStream
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.suspendCoroutine
import kotlin.math.min

class ParallelXZOutputStream(
    context: CoroutineContext,
    private val mainOut: OutputStream,
    options: LZMA2Options = LZMA2Options(),
    private val checkType: Int = XZ.CHECK_CRC64,
    private val blockSize: Int = options.dictSize,
    private val arrayCache: ArrayCache = ArrayCache.getDefaultCache()
) : OutputStream() {
    private val filters = arrayOf(options.filterEncoder)
    private val streamFlags = StreamFlags().apply {
        checkType = this@ParallelXZOutputStream.checkType
    }
    private val index = IndexEncoder()

    private val scope = CoroutineScope(context)
    private val globalCache = BasicByteArrayCache()
    private val writeCache = LocalByteArrayCache(globalCache, 16, 16)

    private val taskQueue = ArrayDeque<EncodeTask>()
    private val taskTailID = AtomicInteger(0)
    private val taskHeadID = AtomicInteger(0)

    private val tempBuffer = ByteArray(1)

    init {
        encodeStreamHeader()
    }

    override fun write(b: Int) {
        tempBuffer[0] = b.toByte()
        write(tempBuffer, 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var offset = off
        var length = len
        while (length > 0) {
            val task = tailTask()
            val writeLen = task.write(b, offset, length)
            if (writeLen == -1) {
                newTask()
                continue
            }
            offset += writeLen
            length -= writeLen
        }
    }

    override fun close() {
        synchronized(taskQueue) { taskQueue.peekLast() }?.finish()
        runBlocking {
            scope.coroutineContext.job.children.forEach {
                it.join()
            }
        }
        index.encode(mainOut)
        encodeStreamFooter()
        mainOut.close()
    }

    private fun tailTask(): EncodeTask {
        var task = synchronized(taskQueue) { taskQueue.peekLast() }
        if (task == null) {
            task = newTask()
        }
        return task
    }

    private fun newTask(): EncodeTask {
        val task = EncodeTask()
        synchronized(taskQueue) { taskQueue.addLast(task) }
        return task
    }

    private fun encodeStreamFlags(buf: ByteArray, off: Int) {
        buf[off] = 0x00
        buf[off + 1] = streamFlags.checkType.toByte()
    }

    private fun encodeStreamHeader() {
        mainOut.write(XZ.HEADER_MAGIC)
        val buf = ByteArray(2)
        encodeStreamFlags(buf, 0)
        mainOut.write(buf)
        EncoderUtil.writeCRC32(mainOut, buf)
    }

    private fun encodeStreamFooter() {
        val buf = ByteArray(6)
        val backwardSize: Long = index.indexSize / 4 - 1
        for (i in 0..3) buf[i] = (backwardSize ushr i * 8).toByte()
        encodeStreamFlags(buf, 4)
        EncoderUtil.writeCRC32(mainOut, buf)
        mainOut.write(buf)
        mainOut.write(XZ.FOOTER_MAGIC)
    }

    private inner class EncodeTask {
        private val id = taskTailID.getAndIncrement()
        private val localCache = LocalByteArrayCache(globalCache, 32, 16)
        private val input = ByteChannel(localCache, writeCache)
        private val output = BufferedOutputStream(this, mainOut, localCache)
        private val encoder = BlockOutputStream(output, filters, Check.getInstance(checkType), arrayCache)

        private val counter = AtomicInteger(0)
        private val signal = AtomicReference<Continuation<Unit>?>(null)
        private var finished = false
        private var count = 0

        private var isHead0 = false

        fun isHead(): Boolean {
            return isHead0 || (taskHeadID.get() == id).also { isHead0 = it }
        }

        init {
            scope.launch {
                while (!finished) {
                    if (counter.getAndDecrement() <= 0) {
                        suspendCoroutine {
                            signal.set(it)
                        }
                    }
                    input.readTo(encoder)
                }
                input.readTo(encoder)
                input.finish(encoder)
                encoder.finish()
                while (!isHead()) {
                    if (counter.getAndDecrement() <= 0) {
                        suspendCoroutine {
                            signal.set(it)
                        }
                    }
                }

                output.flushReal()
                index.add(encoder.unpaddedSize, encoder.uncompressedSize)

                taskHeadID.incrementAndGet()
                synchronized(taskQueue) {
                    taskQueue.removeFirst()
                    taskQueue.peekFirst()?.signal()
                }
            }
        }

        fun write(b: ByteArray, off: Int, len: Int): Int {
            if (count == blockSize) {
                return -1
            }

            val writeLen = min(len, blockSize - count)
            input.write(b, off, writeLen)
            count += writeLen

            if (count == blockSize) {
                finish()
            } else {
                signal()
            }

            return writeLen
        }

        fun finish() {
            if (finished) return
            finished = true
            signal()
        }

        fun signal() {
            counter.incrementAndGet()
            signal.getAndSet(null)?.resumeWith(unitResult)
        }
    }

    private class BufferedOutputStream(
        private val task: EncodeTask,
        private val out: OutputStream,
        private val cache: ByteArrayCache
    ) :
        OutputStream() {
        private val queue = ArrayDeque<ByteArray>()
        private var count = 0
        private var isHead = false

        override fun write(b: Int) {
            if (isHead) {
                out.write(b)
                return
            } else if (task.isHead()) {
                isHead = true
                flushReal()
                out.write(b)
                return
            }

            tail()[count++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            if (isHead) {
                out.write(b, off, len)
                return
            } else if (task.isHead()) {
                isHead = true
                flushReal()
                out.write(b, off, len)
                return
            }

            var offset = off
            var length = len
            while (length > 0) {
                val a = tail()
                val copy = min(length, ByteArrayCache.ARRAY_LENGTH - count)
                System.arraycopy(b, offset, a, count, copy)
                count += copy
                offset += copy
                length -= copy
            }
        }

        private fun tail(): ByteArray {
            if (count == ByteArrayCache.ARRAY_LENGTH) {
                count = 0
                val a = cache.get()
                queue.addLast(a)
                return a
            }

            var a = queue.peekLast()
            if (a == null) {
                a = cache.get()
                queue.addLast(a)
            }
            return a
        }

        fun flushReal() {
            if (queue.isEmpty()) {
                return
            }

            var a = queue.pollFirst()
            while (a != null) {
                val next = queue.pollFirst()
                if (next == null) {
                    out.write(a, 0, count)
                } else {
                    out.write(a)
                }
                cache.put(a)
                a = next
            }
        }
    }


    private class ByteChannel(private val readCache: ByteArrayCache, private val writeCache: ByteArrayCache) {
        private val queue = ArrayDeque<ByteArray>()
        private var buffer: ByteArray? = null
        private var count = 0

        fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var length = len
            while (length > 0) {
                val a = tail()
                val copy = min(length, ByteArrayCache.ARRAY_LENGTH - count)
                System.arraycopy(b, offset, a, count, copy)
                count += copy
                offset += copy
                length -= copy
            }
        }

        private fun tail(): ByteArray {
            if (count == ByteArrayCache.ARRAY_LENGTH) {
                count = 0
                queue.addLast(buffer!!)
                val a = writeCache.get()
                buffer = a
                return a
            }

            var a = buffer
            if (a == null) {
                a = writeCache.get()
                buffer = a
            }
            return a
        }

        fun readTo(out: OutputStream) {
            var a = queue.pollFirst()
            while (a != null) {
                out.write(a)
                readCache.put(a)
                a = queue.pollFirst()
            }
        }

        fun finish(out: OutputStream) {
            out.write(buffer!!, 0, count)
            readCache.put(buffer!!)
            buffer = null
            count = 0
        }
    }

    private interface ByteArrayCache {
        fun get(): ByteArray
        fun put(a: ByteArray)

        companion object {
            const val ARRAY_LENGTH = 1024
        }
    }

    private class BasicByteArrayCache : ByteArrayCache {
        private val list = mutableListOf<ByteArray>()

        override fun get(): ByteArray {
            return list.removeLastOrNull() ?: ByteArray(ByteArrayCache.ARRAY_LENGTH)
        }

        override fun put(a: ByteArray) {
            list.add(a)
        }
    }

    private class LocalByteArrayCache(
        private val parent: ByteArrayCache,
        private val capacity: Int,
        private val cacheCount: Int
    ) : ByteArrayCache {
        private val list = mutableListOf<ByteArray>()

        override fun get(): ByteArray {
            var node = list.removeLastOrNull()
            if (node == null) {
                node = synchronized(parent) {
                    repeat(cacheCount) {
                        list.add(parent.get())
                    }
                    parent.get()
                }
            }
            return node
        }

        override fun put(a: ByteArray) {
            if (list.size < capacity) {
                list.add(a)
            } else {
                synchronized(parent) {
                    parent.put(a)
                }
            }
        }
    }

    companion object {
        val unitResult = Result.success(Unit)
    }
}
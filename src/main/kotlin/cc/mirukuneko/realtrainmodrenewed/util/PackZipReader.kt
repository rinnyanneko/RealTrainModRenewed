package cc.mirukuneko.realtrainmodrenewed.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Reads old RTM add-on packs whose ZIP entry names were often encoded with
 * legacy Japanese code pages rather than UTF-8.
 */
object PackZipReader {
    private val entryNameCharsets: List<Charset> = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("MS932"),
        Charset.forName("Shift_JIS"),
        Charset.forName("GB18030"),
        StandardCharsets.ISO_8859_1,
    )

    @JvmStatic
    @Throws(IOException::class)
    fun read(input: InputStream, consumer: EntryConsumer) {
        read(input.readAllBytes(), consumer)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun read(bytes: ByteArray, consumer: EntryConsumer) {
        val charset = detectEntryNameCharset(bytes)
        ZipInputStream(ByteArrayInputStream(bytes), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                try {
                    consumer.accept(entry, zip)
                } finally {
                    zip.closeEntry()
                }
            }
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun openZipFile(path: Path): ZipFile {
        var last: IOException? = null
        for (charset in entryNameCharsets) {
            try {
                return ZipFile(path.toFile(), charset)
            } catch (e: IOException) {
                if (!looksLikeEntryNameEncodingFailure(e)) {
                    throw e
                }
                last = e
            }
        }
        throw last ?: IOException("Failed to open $path with any supported entry-name encoding")
    }

    @Throws(IOException::class)
    private fun detectEntryNameCharset(bytes: ByteArray): Charset {
        var last: IOException? = null
        for (charset in entryNameCharsets) {
            try {
                validateEntryNames(bytes, charset)
                return charset
            } catch (e: IOException) {
                if (!looksLikeEntryNameEncodingFailure(e)) {
                    throw e
                }
                last = e
            }
        }
        throw last ?: IOException("Failed to decode ZIP entry names with any supported encoding")
    }

    @Throws(IOException::class)
    private fun validateEntryNames(bytes: ByteArray, charset: Charset) {
        ZipInputStream(ByteArrayInputStream(bytes), charset).use { zip ->
            while (zip.nextEntry != null) {
                zip.closeEntry()
            }
        }
    }

    private fun looksLikeEntryNameEncodingFailure(error: IOException): Boolean {
        var current: Throwable? = error
        while (current != null) {
            val message = current.message
            if (message != null) {
                val lower = message.lowercase(Locale.ROOT)
                if (lower.contains("bad entry name") || lower.contains("malformed input")) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }

    fun interface EntryConsumer {
        @Throws(IOException::class)
        fun accept(entry: ZipEntry, input: InputStream)
    }
}

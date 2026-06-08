@file:JvmName("PackTextDecoder")

package cc.mirukuneko.realtrainmodrenewed.util

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.lang.ref.SoftReference
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private const val STREAM_BUFFER_BYTES = 1024 * 1024

private val TEXT_CHARSETS: Array<Charset> = arrayOf(
    StandardCharsets.UTF_8,
    Charset.forName("MS932"),
    Charset.forName("Shift_JIS"),
)

private val STREAM_BUFFER = ThreadLocal<SoftReference<ByteArray>>()

fun decodeText(bytes: ByteArray?): String {
    if (bytes == null || bytes.isEmpty()) {
        return ""
    }
    if (hasUtf8Bom(bytes)) {
        return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
    }
    for (charset in TEXT_CHARSETS) {
        try {
            return decodeStrict(bytes, charset)
        } catch (_: CharacterCodingException) {
        }
    }
    return String(bytes, StandardCharsets.UTF_8)
}

fun decodeJson(bytes: ByteArray?): String {
    return decodeText(bytes)
}

@Throws(IOException::class)
fun readText(path: Path): String {
    return decodeText(Files.readAllBytes(path))
}

@Throws(IOException::class)
fun readText(inputStream: InputStream): String {
    val output = ByteArrayOutputStream(8192)
    val buffer = getStreamBuffer()
    while (true) {
        val read = inputStream.read(buffer, 0, buffer.size)
        if (read < 0) {
            break
        }
        if (read == 0) {
            continue
        }
        output.write(buffer, 0, read)
    }
    return decodeText(output.toByteArray())
}

private fun getStreamBuffer(): ByteArray {
    val reference = STREAM_BUFFER.get()
    var buffer = reference?.get()
    if (buffer == null) {
        buffer = ByteArray(STREAM_BUFFER_BYTES)
        STREAM_BUFFER.set(SoftReference(buffer))
    }
    return buffer
}

private fun hasUtf8Bom(bytes: ByteArray): Boolean {
    return bytes.size >= 3 &&
        (bytes[0].toInt() and 0xFF) == 0xEF &&
        (bytes[1].toInt() and 0xFF) == 0xBB &&
        (bytes[2].toInt() and 0xFF) == 0xBF
}

@Throws(CharacterCodingException::class)
private fun decodeStrict(bytes: ByteArray, charset: Charset): String {
    return charset.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes))
        .toString()
}

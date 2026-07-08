package cc.mirukuneko.realtrainmodrenewed.util

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object PackTextDecoder {
    @JvmStatic
    fun decodeText(bytes: ByteArray): String = decodeJson(bytes)

    @JvmStatic
    fun decodeJson(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val withoutBom = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) bytes.copyOfRange(3, bytes.size) else bytes

        val charsets = listOf(StandardCharsets.UTF_8, Charset.forName("MS932"), Charset.forName("Shift_JIS"))
        for (charset in charsets) {
            try {
                val decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                return decoder.decode(java.nio.ByteBuffer.wrap(withoutBom)).toString()
            } catch (_: Exception) {
            }
        }
        return String(withoutBom, Charset.forName("MS932"))
    }

    @JvmStatic
    fun readText(path: Path): String {
        return try {
            decodeJson(Files.readAllBytes(path))
        } catch (_: Exception) {
            ""
        }
    }

    @JvmStatic
    fun readText(input: InputStream): String {
        return try {
            decodeJson(input.readAllBytes())
        } catch (_: Exception) {
            ""
        }
    }
}

fun decodeText(bytes: ByteArray): String = PackTextDecoder.decodeText(bytes)
fun decodeJson(bytes: ByteArray): String = PackTextDecoder.decodeJson(bytes)
fun readText(path: Path): String = PackTextDecoder.readText(path)
fun readText(input: InputStream): String = PackTextDecoder.readText(input)

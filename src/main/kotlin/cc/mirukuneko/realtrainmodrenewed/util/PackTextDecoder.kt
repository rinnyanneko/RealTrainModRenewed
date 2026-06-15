package cc.mirukuneko.realtrainmodrenewed.util

import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

object PackTextDecoder {
    @JvmStatic
    fun decodeJson(bytes: ByteArray): String {
        // Try UTF-8 first, then Shift-JIS (common in Japanese mod packs)
        return try {
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            try {
                String(bytes, Charset.forName("Shift-JIS"))
            } catch (_: Exception) {
                String(bytes, Charset.defaultCharset())
            }
        }
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

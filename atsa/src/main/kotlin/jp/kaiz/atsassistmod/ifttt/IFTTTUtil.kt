// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt

import com.fasterxml.jackson.databind.ObjectMapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Serialises IFTTT rule containers (gzip + Jackson polymorphic). */
object IFTTTUtil {
    private val mapper = ObjectMapper()

    @JvmStatic
    fun toBytes(container: IFTTTContainer): ByteArray? =
        try {
            compress(mapper.writeValueAsBytes(container))
        } catch (_: IOException) {
            null
        }

    @JvmStatic
    fun fromBytes(bytes: ByteArray): IFTTTContainer? =
        try {
            mapper.readValue(decompress(bytes), IFTTTContainer::class.java)
        } catch (_: IOException) {
            null
        }

    @Throws(IOException::class)
    private fun compress(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { gzip ->
            gzip.write(bytes)
        }
        return out.toByteArray()
    }

    @Throws(IOException::class)
    private fun decompress(compressed: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
            val buffer = ByteArray(1024)
            while (true) {
                val length = gzip.read(buffer)
                if (length <= 0) {
                    break
                }
                out.write(buffer, 0, length)
            }
        }
        return out.toByteArray()
    }
}

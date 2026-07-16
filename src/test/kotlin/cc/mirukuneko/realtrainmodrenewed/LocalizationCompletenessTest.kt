// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalizationCompletenessTest {
    @Test
    fun `supported locales contain every English translation key`() {
        val englishKeys = loadKeys("en_us")

        for (locale in SUPPORTED_LOCALES) {
            val missingKeys = englishKeys - loadKeys(locale)
            assertTrue(
                missingKeys.isEmpty(),
                "$locale is missing translation keys: ${missingKeys.sorted().joinToString()}",
            )
        }
    }

    private fun loadKeys(locale: String): Set<String> {
        val path = "assets/realtrainmodrenewed/lang/$locale.json"
        val stream = assertNotNull(javaClass.classLoader.getResourceAsStream(path), "Missing $path")
        return stream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence()
                .mapNotNull { line -> KEY_PATTERN.find(line)?.groupValues?.get(1) }
                .toSet()
        }
    }

    private companion object {
        val KEY_PATTERN = Regex("""^\s*"([^"]+)"\s*:""")
        val SUPPORTED_LOCALES = listOf("ja_jp", "zh_tw", "zh_cn")
    }
}

// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.rail.util

import kotlin.test.Test
import kotlin.test.assertEquals

class RailPositionContainersTest {
    @Test
    fun `collects positions from nested script-style maps and iterables`() {
        val first = RailPosition(1, 2, 3, 0, 0)
        val second = RailPosition(4, 5, 6, 2, 1)
        val scriptArray = linkedMapOf<Any, Any>(
            "0" to first,
            "1" to listOf(second),
            "length" to 2,
        )

        assertEquals(listOf(first, second), RailPositionContainers.collect(scriptArray))
    }

    @Test
    fun `collects primitive and object arrays without accepting unrelated values`() {
        val first = RailPosition(1, 2, 3, 0, 0)
        val second = RailPosition(4, 5, 6, 2, 1)

        assertEquals(
            listOf(first, second),
            RailPositionContainers.collect(arrayOf(first, "ignored", arrayOf(second))),
        )
    }
}

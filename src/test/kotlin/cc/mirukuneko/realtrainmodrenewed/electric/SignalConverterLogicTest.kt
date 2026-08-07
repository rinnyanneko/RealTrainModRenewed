// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.electric

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SignalConverterLogicTest {
    @Test
    fun incrementPreservesZeroAndHasNoLegacyClamp() {
        assertEquals(0, SignalConverterLogic.transform(SignalConverterType.INCREMENT, 0))
        assertEquals(16, SignalConverterLogic.transform(SignalConverterType.INCREMENT, 15))
    }

    @Test
    fun decrementPreservesZeroAndOne() {
        assertEquals(0, SignalConverterLogic.transform(SignalConverterType.DECREMENT, 0))
        assertEquals(1, SignalConverterLogic.transform(SignalConverterType.DECREMENT, 1))
        assertEquals(14, SignalConverterLogic.transform(SignalConverterType.DECREMENT, 15))
    }

    @Test
    fun comparatorIdsMatchLegacyOrder() {
        assertTrue(SignalComparator.EQUAL.test(4, 4))
        assertTrue(SignalComparator.GREATER_THAN.test(5, 4))
        assertTrue(SignalComparator.GREATER_EQUAL.test(4, 4))
        assertTrue(SignalComparator.LESS_THAN.test(3, 4))
        assertTrue(SignalComparator.LESS_EQUAL.test(4, 4))
        assertTrue(SignalComparator.NOT_EQUAL.test(3, 4))
        assertFalse(SignalComparator.byId(0).test(3, 4))
    }
}

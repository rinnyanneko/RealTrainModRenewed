// SPDX-License-Identifier: LGPL-3.0-or-later
package cc.mirukuneko.realtrainmodrenewed.script

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegacyScriptCircuitBreakerTest {
    @Test
    fun `five consecutive failures disable only the failing phase`() {
        val engine = Any()
        val breaker = LegacyScriptCircuitBreaker()

        repeat(4) {
            assertFalse(breaker.recordFailure(engine, "render", IllegalStateException()).disabled)
        }
        assertTrue(breaker.recordFailure(engine, "render", IllegalStateException()).disabled)
        assertTrue(breaker.isDisabled(engine, "render"))
        assertFalse(breaker.isDisabled(engine, "tick"))
    }

    @Test
    fun `success resets the consecutive failure count`() {
        val engine = Any()
        val breaker = LegacyScriptCircuitBreaker()

        repeat(4) { breaker.recordFailure(engine, "tick", IllegalStateException()) }
        breaker.recordSuccess(engine, "tick")
        repeat(4) {
            assertFalse(breaker.recordFailure(engine, "tick", IllegalStateException()).disabled)
        }
    }

    @Test
    fun `fatal errors disable a phase immediately including wrapped errors`() {
        val engine = Any()
        val breaker = LegacyScriptCircuitBreaker()
        val wrapped = RuntimeException("script wrapper", StackOverflowError())

        val result = breaker.recordFailure(engine, "server", wrapped)

        assertTrue(result.fatal)
        assertTrue(result.disabled)
        assertTrue(breaker.isDisabled(engine, "server"))
    }
}

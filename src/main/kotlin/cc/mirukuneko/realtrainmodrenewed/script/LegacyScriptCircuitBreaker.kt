// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.script

import java.util.concurrent.ConcurrentHashMap

/**
 * Stops one script call family after repeated consecutive failures.
 *
 * A render failure must not disable a working sound/update hook, so state is
 * tracked per engine identity and phase family. Fatal VM errors are disabled
 * immediately because retrying them every tick can make the game unplayable.
 */
internal class LegacyScriptCircuitBreaker(private val failureLimit: Int = 5) {
    init {
        require(failureLimit > 0)
    }

    private val failureCounts = ConcurrentHashMap<String, Int>()
    private val disabled = ConcurrentHashMap.newKeySet<String>()

    fun isDisabled(engine: Any, phase: String): Boolean = disabled.contains(key(engine, phase))

    fun recordSuccess(engine: Any, phase: String) {
        val key = key(engine, phase)
        if (!disabled.contains(key)) {
            failureCounts.remove(key)
        }
    }

    fun recordFailure(engine: Any, phase: String, error: Throwable): Failure {
        val key = key(engine, phase)
        val fatal = isFatal(error)
        val count = if (fatal) {
            failureLimit
        } else {
            failureCounts.merge(key, 1, Int::plus) ?: 1
        }
        val disabledNow = count >= failureLimit
        val newlyDisabled = disabledNow && disabled.add(key)
        if (disabledNow) {
            failureCounts.remove(key)
        }
        return Failure(count, fatal, disabledNow, newlyDisabled)
    }

    private fun key(engine: Any, phase: String): String =
        "${System.identityHashCode(engine)}\u0000$phase"

    private fun isFatal(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(16) {
            when (current) {
                is StackOverflowError, is OutOfMemoryError -> return true
                null -> return false
            }
            val next = current.cause
            if (next === current) return false
            current = next
        }
        return false
    }

    data class Failure(
        val count: Int,
        val fatal: Boolean,
        val disabled: Boolean,
        val newlyDisabled: Boolean,
    )
}

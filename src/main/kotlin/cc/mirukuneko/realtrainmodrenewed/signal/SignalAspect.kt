// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.signal

/**
 * Signal aspects used by RTM-style remote signals.
 * Button order, IDs, and legacy values are save/UI compatibility-sensitive.
 */
enum class SignalAspect(
    @get:JvmName("getId")
    val id: Int,
    @get:JvmName("getLabel")
    val label: String,
    val translationKey: String,
    @get:JvmName("getLegacyValue")
    val legacyValue: Int,
) {
    STOP(0, "停止(R)", "signal.realtrainmodrenewed.aspect.stop", 1),
    WARNING(1, "警戒(YY)", "signal.realtrainmodrenewed.aspect.warning", 4),
    CAUTION(2, "注意(Y)", "signal.realtrainmodrenewed.aspect.caution", 3),
    REDUCE(3, "減速(YG)", "signal.realtrainmodrenewed.aspect.reduce", 6),
    RESTRICTED(4, "抑速", "signal.realtrainmodrenewed.aspect.restricted", 2),
    PROCEED(5, "進行(G)", "signal.realtrainmodrenewed.aspect.proceed", 5),
    HIGH_SPEED(6, "高速進行(GG)", "signal.realtrainmodrenewed.aspect.high_speed", 7);

    companion object {
        @JvmStatic
        fun byId(id: Int): SignalAspect {
            return entries.firstOrNull { it.id == id } ?: STOP
        }

        @JvmStatic
        fun byLegacyValue(legacyValue: Int): SignalAspect {
            return entries.firstOrNull { it.legacyValue == legacyValue } ?: STOP
        }
    }
}

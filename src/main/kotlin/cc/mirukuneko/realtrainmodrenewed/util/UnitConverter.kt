// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.util

import kotlin.math.roundToInt as kotlinRoundToInt

object UnitConverter {
    @JvmStatic
    fun cm2m(cm: Float): Float = cm / 100.0f

    @JvmStatic
    fun kph2bpt(kph: Float): Float = (kph * 1000.0f / 3600.0f) / 20.0f

    @JvmStatic
    fun s2t(seconds: Float): Float = seconds * 20.0f

    @JvmStatic
    fun mpss2bpts(mpss: Float): Float = mpss / (20.0f * 20.0f)
}

fun cm2m(cm: Float): Float = UnitConverter.cm2m(cm)
fun kph2bpt(kph: Float): Float = UnitConverter.kph2bpt(kph)
fun s2t(seconds: Float): Float = UnitConverter.s2t(seconds)
fun mpss2bpts(mpss: Float): Float = UnitConverter.mpss2bpts(mpss)

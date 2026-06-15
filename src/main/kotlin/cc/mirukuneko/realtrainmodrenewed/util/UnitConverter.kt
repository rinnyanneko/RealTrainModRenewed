package cc.mirukuneko.realtrainmodrenewed.util

import kotlin.math.roundToInt as kotlinRoundToInt

object UnitConverter {
    @JvmStatic
    fun cm2m(cm: Float): Float = cm / 100.0f

    @JvmStatic
    fun kph2bpt(kph: Float): Float = (kph * 1000.0f / 3600.0f) / (1.0f / 20.0f)

    @JvmStatic
    fun mpss2bpts(mpss: Float): Float = mpss / (1.0f / 20.0f)
}

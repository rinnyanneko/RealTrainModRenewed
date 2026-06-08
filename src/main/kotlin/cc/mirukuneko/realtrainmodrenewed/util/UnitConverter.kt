@file:JvmName("UnitConverter")

package cc.mirukuneko.realtrainmodrenewed.util

import cc.mirukuneko.realtrainmodrenewed.util.RealTrainModRenewedConstants.TICK_PER_SECOND

/** Converts kilometres per hour to blocks per tick. */
fun kph2bpt(kilometrePerHour: Float): Float {
    return kilometrePerHour / 3.6f / TICK_PER_SECOND
}

/** Converts centimetres to metres. */
fun cm2m(centimetre: Float): Float {
    return centimetre / 100.0f
}

/** Converts seconds to ticks. */
fun s2t(second: Float): Float {
    return second * TICK_PER_SECOND
}

/** Converts metres per second squared to blocks per tick squared. */
fun mpss2bpts(meterPerSecondSquared: Float): Float {
    return meterPerSecondSquared / (TICK_PER_SECOND * TICK_PER_SECOND)
}

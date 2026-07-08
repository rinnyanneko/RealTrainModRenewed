package cc.mirukuneko.realtrainmodrenewed.rail.util

/**
 * Simplified ResourceStateRail stand-in: ballast width from rail pack (legacy RailConfig#ballastWidth).
 */
class RailProperties {
    /** Same semantics as legacy: full width is 2*halfWidth + center; 0 = center column only. */
    @JvmField
    var ballastWidth: Int = 0

    @JvmField
    var blockHeight: Float = 0.0625F

    companion object {
        @JvmStatic
        fun createDefault(): RailProperties = RailProperties().apply {
            ballastWidth = 0
            blockHeight = 0.0625F
        }
    }
}

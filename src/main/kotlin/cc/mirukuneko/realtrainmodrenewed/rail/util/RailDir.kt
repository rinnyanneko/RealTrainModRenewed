package cc.mirukuneko.realtrainmodrenewed.rail.util

enum class RailDir(val id: Byte) {
    RIGHT(-1),
    LEFT(1),
    NONE(0);

    fun invert(): RailDir = when (this) {
        RIGHT -> LEFT
        LEFT -> RIGHT
        NONE -> NONE
    }
}

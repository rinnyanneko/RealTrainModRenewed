package cc.mirukuneko.realtrainmodrenewed.rail.math

interface ILine {
    fun getPoint(split: Int, index: Int): DoubleArray
    fun getNearlestPoint(split: Int, x: Double, z: Double): Int
    fun getSlope(split: Int, index: Int): Double
    val length: Double
}

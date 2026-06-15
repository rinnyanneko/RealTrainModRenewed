package cc.mirukuneko.realtrainmodrenewed.rail.util

import cc.mirukuneko.realtrainmodrenewed.block.BallastBlock
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock
import cc.mirukuneko.realtrainmodrenewed.block.MarkerBlock
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlocks
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import cc.mirukuneko.realtrainmodrenewed.rail.math.BezierCurve
import cc.mirukuneko.realtrainmodrenewed.rail.math.CurveMath
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import java.util.*
import kotlin.math.*

/**
 * legacy RailMap 移植。道床用の座標列生成（レガシー）と、レール配置可否の判定を担当。
 * レールビジュアル(MQOモデル)は LargeRailCoreBlockEntity が別途担当する。
 * 道床ブロックのワールド配置は行わない（軽量化のため中心線のみ検査・撤去）。
 */
abstract class RailMap {
    companion object {
        @JvmField
        val suppressRailRemoval: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

        /**
         * 曲線計算に渡す分割数。[BezierCurve] の内部 split と一致させる。
         */
        @JvmStatic
        fun curveSplitForLength(length: Double): Int = BezierCurve.splitForLength(length)
    }

    protected val rails: MutableList<IntArray> = ArrayList()

    abstract val startRP: RailPosition
    abstract val endRP: RailPosition
    abstract fun getLength(): Double
    abstract fun getNearlestPoint(split: Int, x: Double, z: Double): Int
    abstract fun getRailPos(split: Int, index: Int): DoubleArray
    abstract fun getRailHeight(split: Int, index: Int): Double
    abstract fun getRailYaw(split: Int, index: Int): Float
    abstract fun getRailPitch(split: Int, index: Int): Float
    abstract fun getRailRoll(split: Int, index: Int): Float

    open fun getCant(split: Int, index: Int): Float = getRailRoll(split, index)

    /** legacy の getRailRotation と同じ（ヨー角）。 */
    fun getRailRotation(split: Int, index: Int): Float = getRailYaw(split, index)

    /** 水平が直線のレール区間か（見た目のゲージ調整用）。 */
    open val isStraightTrack: Boolean get() = false

    /**
     * 水平ベジェの弧長に基づく分割数。
     * getLength() は勾配で 3D 長になるため、曲線のサンプル数には getHorizontalPathLength() を使うこと。
     */
    open fun getHorizontalPathLength(): Double = getLength()

    override fun equals(other: Any?): Boolean {
        if (other !is RailMap) return false
        return startRP == other.startRP && endRP == other.endRP
    }

    override fun hashCode(): Int {
        var result = startRP.hashCode()
        result = 31 * result + endRP.hashCode()
        return result
    }

    /**
     * 道床ブロック位置リストを生成する。
     * ballastWidth に応じて線路幅方向にブロックを展開する。
     */
    protected fun createRailList(prop: RailProperties) {
        rails.clear()
        val halfW = max(prop.ballastWidth / 2.0, 0.6)
        var split = (getLength() * 4.0).toInt()
        if (split < 2) split = 2
        val n = split + 1

        val sx = DoubleArray(n)
        val sz = DoubleArray(n)
        val sy = IntArray(n)
        val soff = DoubleArray(n)
        var minx = Double.MAX_VALUE; var maxx = -Double.MAX_VALUE
        var minz = Double.MAX_VALUE; var maxz = -Double.MAX_VALUE
        for (j in 0 until n) {
            val point = getRailPos(split, j)
            sx[j] = point[1]
            sz[j] = point[0]
            val h = getRailHeight(split, j)
            sy[j] = floor(h + 1.0e-4).toInt()
            soff[j] = h - sy[j]
            if (sx[j] < minx) minx = sx[j]
            if (sx[j] > maxx) maxx = sx[j]
            if (sz[j] < minz) minz = sz[j]
            if (sz[j] > maxz) maxz = sz[j]
        }

        val bx0 = CurveMath.floor(minx - halfW - 1.0)
        val bx1 = CurveMath.floor(maxx + halfW + 1.0)
        val bz0 = CurveMath.floor(minz - halfW - 1.0)
        val bz1 = CurveMath.floor(maxz + halfW + 1.0)
        val thrSq = halfW * halfW

        for (X in bx0..bx1) {
            for (Z in bz0..bz1) {
                val cx = X + 0.5
                val cz = Z + 0.5
                var best = Double.MAX_VALUE
                var bestJ = 0
                for (j in 0 until n) {
                    val dx = cx - sx[j]
                    val dz = cz - sz[j]
                    val d = dx * dx + dz * dz
                    if (d < best) {
                        best = d
                        bestJ = j
                    }
                }
                if (best > thrSq) continue
                if (bestJ == 0 || bestJ == n - 1) {
                    val inner = if (bestJ == 0) minOf(1, n - 1) else maxOf(0, n - 2)
                    val tinx = sx[inner] - sx[bestJ]
                    val tinz = sz[inner] - sz[bestJ]
                    val vx = cx - sx[bestJ]
                    val vz = cz - sz[bestJ]
                    if (vx * tinx + vz * tinz < -1.0e-3) continue
                }
                for (j in 0 until n) {
                    val dx = cx - sx[j]
                    val dz = cz - sz[j]
                    if (dx * dx + dz * dz > thrSq) continue
                    var off16 = (soff[j] * 16.0).roundToInt().coerceIn(0, 15)
                    addRailBlock(X, sy[j], Z, off16)
                }
            }
        }
    }

    protected fun addRailBlock(x: Int, y: Int, z: Int) = addRailBlock(x, y, z, 0)

    protected fun addRailBlock(x: Int, y: Int, z: Int, surfaceOffset16: Int) {
        for (i in rails.indices) {
            val ia = rails[i]
            if (ia[0] == x && ia[1] == y && ia[2] == z) {
                if (ia.size >= 4 && surfaceOffset16 > ia[3]) {
                    ia[3] = surfaceOffset16
                }
                return
            }
        }
        rails.add(intArrayOf(x, y, z, surfaceOffset16))
    }

    /**
     * 道床(見える砂利)は置かない (ユーザー要望「道床を消す」)。
     */
    fun setRail(level: Level?, ballastBlock: Block?, x0: Int, y0: Int, z0: Int, prop: RailProperties?) {
        if (level == null || prop == null) {
            rails.clear()
            return
        }
        createRailList(prop)
        val collisionBlock = RealTrainModRenewedBlocks.RAIL_COLLISION.get()
        val corePos = BlockPos(x0, y0, z0)
        for (rail in rails) {
            val pos = BlockPos(rail[0], rail[1], rail[2])
            val existingState = level.getBlockState(pos)
            val existing = existingState.block
            val replaceable = existing == Blocks.AIR
                || existing == Blocks.CAVE_AIR || existing == Blocks.VOID_AIR
                || existing is RailCollisionBlock
                || existing is BallastBlock
                || existing is MarkerBlock
                || existingState.canBeReplaced()
            if (replaceable) {
                level.setBlock(pos, collisionBlock.defaultBlockState(), Block.UPDATE_ALL)
                val be = level.getBlockEntity(pos)
                if (be is RailCollisionBlockEntity) {
                    be.corePos = corePos
                    val surfaceY = if (rail.size >= 4) rail[3] / 16.0f else 0.0f
                    be.surfaceY = surfaceY
                    level.sendBlockUpdated(pos, be.blockState, be.blockState, Block.UPDATE_ALL)
                }
            }
        }
        rails.clear()
    }

    /**
     * レールを置けるか。
     */
    fun canPlaceRail(level: Level, isCreative: Boolean, prop: RailProperties): Boolean {
        val len = getLength()
        val samples = maxOf(3, ceil(len).toInt() + 1)
        val split = curveSplitForLength(getHorizontalPathLength())
        val startNeighbor = startRP.getNeighborBlockPos()
        val endNeighbor = endRP.getNeighborBlockPos()
        var allClear = true
        for (i in 0 until samples) {
            val j = if (samples <= 1) 0 else (split.toDouble() * i / (samples - 1)).roundToInt()
            val jc = j.coerceAtMost(split)
            val point = getRailPos(split, jc)
            val x = CurveMath.floor(point[1])
            val z = CurveMath.floor(point[0])
            val y = getRailHeight(split, jc).toInt()
            val pos = BlockPos(x, y, z)
            if (pos == startNeighbor || pos == endNeighbor) continue
            val state = level.getBlockState(pos)
            val block = state.block
            val passable = block == Blocks.AIR
                || block == Blocks.CAVE_AIR
                || block == Blocks.VOID_AIR
                || block is MarkerBlock
                || block is BallastBlock
                || block is RailCollisionBlock
                || state.canBeReplaced()
            if (!passable) {
                allClear = false
                if (!isCreative) return false
            }
        }
        return isCreative || allClear
    }

    fun getRailBlockList(prop: RailProperties, regenerate: Boolean): List<IntArray> {
        if (rails.isEmpty() || regenerate) {
            createRailList(prop)
        }
        return ArrayList(rails)
    }

    /** 旧道床ブロックを中心線沿いに軽量スキャンして撤去する。 */
    fun removeRailBlocks(level: Level) {
        val len = getLength()
        val split = curveSplitForLength(getHorizontalPathLength())
        val samples = maxOf(3, split + 1)
        for (i in 0 until samples) {
            val j = if (samples <= 1) 0 else (split.toDouble() * i / (samples - 1)).roundToInt()
            val jc = j.coerceAtMost(split)
            val point = getRailPos(split, jc)
            val x = CurveMath.floor(point[1])
            val z = CurveMath.floor(point[0])
            val y = getRailHeight(split, jc).toInt()
            for (dx in -1..1) {
                for (dz in -1..1) {
                    for (dy in -1..0) {
                        val pos = BlockPos(x + dx, y + dy, z + dz)
                        val block = level.getBlockState(pos).block
                        if (block is BallastBlock || block is RailCollisionBlock) {
                            level.removeBlock(pos, false)
                        }
                    }
                }
            }
        }
    }
}

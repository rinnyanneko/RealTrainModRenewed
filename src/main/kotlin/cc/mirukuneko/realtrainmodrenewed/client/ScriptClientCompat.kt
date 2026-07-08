package cc.mirukuneko.realtrainmodrenewed.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.Locale

/**
 * 1.12.2 RTM script (SuperRailBuilder3 render script, etc.) bridge
 * for NGTUtilClient.getMinecraft() / MCWrapperClient.getPlayer() style
 * access into the 1.21.1 client instance. Client-only helper.
 */
class ScriptClientCompat {
    companion object {
        /**
         * Current render partialTick. CarRenderer.render sets this each frame.
         * EntityRenderDispatcher places PoseStack origin at lerp(partialTick, xOld, getX()),
         * so renderPosX uses the same partialTick to fully cancel the origin and pin
         * fixed markers to true world coordinates (using getTimer's separate partialTick
         * would introduce a small offset that causes jitter).
         */
        @JvmField
        @Volatile
        var currentRenderPartialTick: Float = 1.0F
    }

    /**
     * Entity render-interpolated position (matching PoseStack origin).
     * SRB markers are drawn relative to entity position, so we must return
     * the interpolated position, not the tick position (getX), to avoid
     * visual desync/jitter.
     */
    fun renderPosX(e: Any?): Double = renderPos(e, 0)
    fun renderPosY(e: Any?): Double = renderPos(e, 1)
    fun renderPosZ(e: Any?): Double = renderPos(e, 2)

    private fun renderPos(e: Any?, axis: Int): Double {
        val ent = e as? Entity ?: return 0.0
        return try {
            val pt = currentRenderPartialTick.toDouble()
            when (axis) {
                0 -> Mth.lerp(pt, ent.xOld, ent.x)
                1 -> Mth.lerp(pt, ent.yOld, ent.y)
                else -> Mth.lerp(pt, ent.zOld, ent.z)
            }
        } catch (_: Throwable) {
            when (axis) {
                0 -> ent.x
                1 -> ent.y
                else -> ent.z
            }
        }
    }

    /** Client local player (null if unavailable). */
    fun getPlayer(): Any? {
        return try {
            Minecraft.getInstance().player
        } catch (_: Throwable) {
            null
        }
    }

    /** Current open screen / GUI (null if none). RTM field_71462_r equivalent. */
    fun getCurrentScreen(): Any? {
        return try {
            Minecraft.getInstance().screen
        } catch (_: Throwable) {
            null
        }
    }

    /** Client level (null if unavailable). */
    fun getLevel(): Any? {
        return try {
            Minecraft.getInstance().level
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Player eye raycast (SRB BlockUtil.getMOPFromPlayer equivalent).
     * Returns hit location when a block is hit, or null when looking at the sky.
     */
    fun raycast(distance: Double): RaycastResult? {
        return try {
            val mc = Minecraft.getInstance()
            val p: Player = mc.player ?: return null
            val level = mc.level ?: return null
            // Use partialTick=1.0 (current tick position/orientation) matching
            // original NGT BlockUtil.getMOPFromPlayer. Client player orientation
            // updates every frame (instant mouse response), so this matches the
            // crosshair. Interpolated partialTick would lag the orientation and
            // cause the guide line to jitter wildly during panning.
            val eye = p.getEyePosition(1.0F)
            val look = p.getViewVector(1.0F)
            val end = eye.add(look.x * distance, look.y * distance, look.z * distance)
            val hit: BlockHitResult = level.clip(
                ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p)
            )
            if (hit.type == HitResult.Type.BLOCK) {
                val loc = hit.location
                val bp = hit.blockPos
                RaycastResult(true, loc.x, loc.y, loc.z, bp.x, bp.y, bp.z)
            } else {
                // No block hit (looking at sky, etc.). Returning a far-away point
                // would cause the cursor to jump and the angle-change jitter.
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** LWJGL2 Mouse.isButtonDown equivalent (0=left,1=right,2=middle). Reads GLFW mouse button state. */
    fun isMouseDown(button: Int): Boolean {
        return try {
            val mc = Minecraft.getInstance()
            GLFW.glfwGetMouseButton(mc.window.handle(), button) == GLFW.GLFW_PRESS
        } catch (_: Throwable) {
            false
        }
    }

    /** Current language code (e.g. "en_us"). RTM getLanguageManager chain replacement. */
    fun getLanguageCode(): String {
        return try {
            Minecraft.getInstance().languageManager.selected.lowercase(Locale.ROOT)
        } catch (_: Throwable) {
            "en_us"
        }
    }

    /** Raycast result holder (JS can extract hitVec/BlockPos). */
    class RaycastResult internal constructor(
        val isHit: Boolean,
        val hitX: Double,
        val hitY: Double,
        val hitZ: Double,
        val blockX: Int,
        val blockY: Int,
        val blockZ: Int
    )
}

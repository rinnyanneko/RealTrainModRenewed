package cc.mirukuneko.realtrainmodrenewed.client.renderer

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.world.level.block.entity.BlockEntity

class LegacyBlockEntityRenderState<T : BlockEntity> : BlockEntityRenderState() {
    @JvmField var blockEntity: T? = null
    @JvmField var partialTick: Float = 0f
}

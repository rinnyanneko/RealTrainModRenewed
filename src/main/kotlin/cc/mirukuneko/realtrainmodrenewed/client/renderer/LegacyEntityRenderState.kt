package cc.mirukuneko.realtrainmodrenewed.client.renderer

import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.world.entity.Entity

class LegacyEntityRenderState<T : Entity> : EntityRenderState() {
    @JvmField var entity: T? = null
    @JvmField var entityYaw: Float = 0f
}

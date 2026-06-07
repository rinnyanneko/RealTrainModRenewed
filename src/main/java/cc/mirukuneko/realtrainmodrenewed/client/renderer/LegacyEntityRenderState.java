package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;

public final class LegacyEntityRenderState<T extends Entity> extends EntityRenderState {
    T entity;
    float entityYaw;
}

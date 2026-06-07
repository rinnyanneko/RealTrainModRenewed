package cc.mirukuneko.realtrainmodrenewed.client.renderer;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class LegacyBlockEntityRenderState<T extends BlockEntity> extends BlockEntityRenderState {
    T blockEntity;
    float partialTick;
}

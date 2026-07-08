// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.render;

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class LegacyBlockEntityRenderState<T extends BlockEntity> extends BlockEntityRenderState {
    T blockEntity;
    float partialTick;
}

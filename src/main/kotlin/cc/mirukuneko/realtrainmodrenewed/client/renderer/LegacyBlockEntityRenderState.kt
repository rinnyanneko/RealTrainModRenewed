// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.client.renderer

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.world.level.block.entity.BlockEntity

class LegacyBlockEntityRenderState<T : BlockEntity> : BlockEntityRenderState() {
    @JvmField var blockEntity: T? = null
    @JvmField var partialTick: Float = 0f
}

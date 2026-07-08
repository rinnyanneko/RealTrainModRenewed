// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.blockentity

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.max
import kotlin.math.min

class RailCollisionBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.RAIL_COLLISION.get(), pos, state) {

    @JvmField
    var corePos: BlockPos? = null
    @JvmField
    var surfaceY: Float = 0f

    fun setCorePos(corePos: BlockPos?) { this.corePos = corePos; setChanged() }
    fun getCorePos(): BlockPos? = corePos
    fun setSurfaceY(surfaceY: Float) { this.surfaceY = max(0f, min(1f, surfaceY)); setChanged() }
    fun getSurfaceY(): Float = surfaceY

    override fun saveAdditional(tag: ValueOutput) {
        super.saveAdditional(tag)
        corePos?.let { tag.putIntArray("CorePos", intArrayOf(it.x, it.y, it.z)) }
        tag.putFloat("SurfaceY", surfaceY)
    }

    override fun loadAdditional(tag: ValueInput) {
        super.loadAdditional(tag)
        tag.getIntArray("CorePos").ifPresent { a -> if (a.size >= 3) corePos = BlockPos(a[0], a[1], a[2]) }
        surfaceY = tag.getFloatOr("SurfaceY", surfaceY)
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}

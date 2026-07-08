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

class SignalRemoteBlockEntity(pos: BlockPos, state: BlockState) :
    BlockEntity(RealTrainModRenewedBlockEntities.SIGNAL_REMOTE.get(), pos, state) {

    @JvmField var linkedChannel: Int = -1

    fun getLinkedChannel(): Int = linkedChannel
    fun setLinkedChannel(channel: Int) { linkedChannel = channel; setChanged() }

    override fun saveAdditional(tag: ValueOutput) { super.saveAdditional(tag); tag.putInt("LinkedChannel", linkedChannel) }
    override fun loadAdditional(tag: ValueInput) { super.loadAdditional(tag); linkedChannel = tag.getIntOr("LinkedChannel", -1) }
    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = saveWithoutMetadata(registries)
    override fun getUpdatePacket(): ClientboundBlockEntityDataPacket = ClientboundBlockEntityDataPacket.create(this)
}

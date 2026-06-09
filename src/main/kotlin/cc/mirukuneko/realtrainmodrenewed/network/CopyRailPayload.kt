package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock
import cc.mirukuneko.realtrainmodrenewed.blockentity.LargeRailCoreBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.RailCollisionBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

data class CopyRailPayload(val pos: BlockPos) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<CopyRailPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "copy_rail")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, CopyRailPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ::CopyRailPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: CopyRailPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player()
                var corePos = payload.pos
                val direct = player.level().getBlockEntity(payload.pos)
                val core = when (direct) {
                    is LargeRailCoreBlockEntity -> direct
                    is RailCollisionBlockEntity -> {
                        corePos = direct.corePos
                        player.level().getBlockEntity(corePos) as? LargeRailCoreBlockEntity
                    }
                    else -> null
                } ?: return@enqueueWork

                val clone = LargeRailCoreBlock.createRailCloneStack(corePos, core)
                if (clone.isEmpty) {
                    return@enqueueWork
                }
                player.inventory.setItem(player.inventory.selectedSlot, clone)
                player.containerMenu.broadcastChanges()
            }
        }
    }
}

package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedItems
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

class MountTrainPayload : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val INSTANCE = MountTrainPayload()

        @JvmField
        val TYPE: CustomPacketPayload.Type<MountTrainPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "mount_train")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, MountTrainPayload> = StreamCodec.unit(INSTANCE)

        @JvmStatic
        fun handleOnServer(payload: MountTrainPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val holdingCrowbar = player.mainHandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get()) ||
                    player.offhandItem.`is`(RealTrainModRenewedItems.CROWBAR_ITEM.get())
                if (holdingCrowbar) {
                    TrainEntity.tryEnterCouplingModeFromPlayerView(player)
                } else {
                    TrainEntity.tryRideFromPlayerView(player)
                }
            }
        }
    }
}

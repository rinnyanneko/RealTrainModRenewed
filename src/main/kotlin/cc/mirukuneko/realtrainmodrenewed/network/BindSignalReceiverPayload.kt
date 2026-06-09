package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalStateBlockEntity
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class BindSignalReceiverPayload(
    val pos: BlockPos,
    val channel: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<BindSignalReceiverPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "bind_signal_receiver")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, BindSignalReceiverPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.INT,
            { payload -> payload.channel },
            ::BindSignalReceiverPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: BindSignalReceiverPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val data = SignalNetworkSavedData.get(player.level())
                if (!data.hasChannel(payload.channel)) {
                    player.sendOverlayMessage(Component.literal("その番号の信号は見つかりません"))
                    return@enqueueWork
                }
                when (val blockEntity = player.level().getBlockEntity(payload.pos)) {
                    is SignalRemoteBlockEntity -> {
                        blockEntity.linkedChannel = payload.channel
                        player.level().sendBlockUpdated(payload.pos, blockEntity.blockState, blockEntity.blockState, 3)
                    }
                    is SignalStateBlockEntity -> {
                        blockEntity.linkedChannel = payload.channel
                        player.level().sendBlockUpdated(payload.pos, blockEntity.blockState, blockEntity.blockState, 3)
                    }
                    else -> return@enqueueWork
                }
                player.sendOverlayMessage(Component.literal("信号番号 ${payload.channel} を受信しました"))
            }
        }
    }
}

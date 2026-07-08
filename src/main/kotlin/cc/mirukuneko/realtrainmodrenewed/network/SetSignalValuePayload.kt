package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
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

data class SetSignalValuePayload(
    val pos: BlockPos,
    val signalValue: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetSignalValuePayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "set_signal_value")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SetSignalValuePayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.INT,
            { payload -> payload.signalValue },
            ::SetSignalValuePayload,
        )

        @JvmStatic
        fun handleOnServer(payload: SetSignalValuePayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val blockEntity = player.level().getBlockEntity(payload.pos) as? SignalRemoteBlockEntity ?: return@enqueueWork
                val linkedChannel = blockEntity.linkedChannel
                if (linkedChannel <= 0) {
                    player.sendOverlayMessage(Component.literal("先に信号番号を入力してください"))
                    return@enqueueWork
                }
                val data = SignalNetworkSavedData.get(player.level())
                if (!data.hasChannel(linkedChannel)) {
                    player.sendOverlayMessage(Component.literal("信号番号が無効になっています"))
                    return@enqueueWork
                }
                val aspect = SignalAspect.byLegacyValue(payload.signalValue)
                data.setAspect(player.level().server, linkedChannel, aspect)
                player.sendOverlayMessage(Component.literal("signal値 ${payload.signalValue} を送信しました"))
            }
        }
    }
}

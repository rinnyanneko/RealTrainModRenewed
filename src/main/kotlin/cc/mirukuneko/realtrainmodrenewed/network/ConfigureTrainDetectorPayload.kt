package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.TrainDetectorBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

@JvmRecord
data class ConfigureTrainDetectorPayload(
    val pos: BlockPos,
    val channel: Int,
    val range: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ConfigureTrainDetectorPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "configure_train_detector")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, ConfigureTrainDetectorPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.INT,
            { payload -> payload.channel },
            ByteBufCodecs.INT,
            { payload -> payload.range },
            ::ConfigureTrainDetectorPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: ConfigureTrainDetectorPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val blockEntity = player.level().getBlockEntity(payload.pos) as? TrainDetectorBlockEntity ?: return@enqueueWork
                blockEntity.configure(payload.channel, payload.range)
                player.level().sendBlockUpdated(payload.pos, blockEntity.blockState, blockEntity.blockState, 3)
                player.sendOverlayMessage(Component.literal("電車検知ブロックを更新しました"))
            }
        }
    }
}

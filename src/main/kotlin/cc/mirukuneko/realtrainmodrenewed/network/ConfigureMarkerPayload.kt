package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.MarkerBlockEntity
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Wrench config GUI to server: updates marker anchor curve and cant values. */
@JvmRecord
data class ConfigureMarkerPayload(
    val pos: BlockPos,
    val anchorYaw: Float,
    val anchorPitch: Float,
    val anchorLengthHorizontal: Float,
    val anchorLengthVertical: Float,
    val cantCenter: Float,
    val cantEdge: Float,
    val cantRandom: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ConfigureMarkerPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "configure_marker")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, ConfigureMarkerPayload> = StreamCodec.of(
            { buf, payload ->
                BlockPos.STREAM_CODEC.encode(buf, payload.pos)
                buf.writeFloat(payload.anchorYaw)
                buf.writeFloat(payload.anchorPitch)
                buf.writeFloat(payload.anchorLengthHorizontal)
                buf.writeFloat(payload.anchorLengthVertical)
                buf.writeFloat(payload.cantCenter)
                buf.writeFloat(payload.cantEdge)
                buf.writeFloat(payload.cantRandom)
            },
            { buf ->
                ConfigureMarkerPayload(
                    BlockPos.STREAM_CODEC.decode(buf),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                )
            },
        )

        @JvmStatic
        fun handleOnServer(payload: ConfigureMarkerPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val marker = player.level().getBlockEntity(payload.pos) as? MarkerBlockEntity ?: return@enqueueWork
                marker.configure(
                    payload.anchorYaw,
                    payload.anchorPitch,
                    payload.anchorLengthHorizontal,
                    payload.anchorLengthVertical,
                    payload.cantCenter,
                    payload.cantEdge,
                    payload.cantRandom,
                )
                player.sendOverlayMessage(Component.literal("マーカー設定を更新しました"))
            }
        }
    }
}

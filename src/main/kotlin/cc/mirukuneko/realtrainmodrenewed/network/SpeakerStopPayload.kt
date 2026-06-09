package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SpeakerStopPayload(
    val x: Double,
    val y: Double,
    val z: Double,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SpeakerStopPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "speaker_stop")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SpeakerStopPayload> = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            { payload -> payload.x },
            ByteBufCodecs.DOUBLE,
            { payload -> payload.y },
            ByteBufCodecs.DOUBLE,
            { payload -> payload.z },
            ::SpeakerStopPayload,
        )

        @JvmStatic
        fun handleOnClient(payload: SpeakerStopPayload, context: IPayloadContext) {
            context.enqueueWork {
                LegacyScriptSoundManager.stopAt(payload.x, payload.y, payload.z)
            }
        }
    }
}

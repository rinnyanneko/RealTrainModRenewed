package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server-to-client packet that plays a speaker sound at a fixed position. */
@JvmRecord
data class SpeakerPlayPayload(
    val x: Double,
    val y: Double,
    val z: Double,
    val soundId: String,
    val volume: Float,
    val pitch: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SpeakerPlayPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "speaker_play")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SpeakerPlayPayload> = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            { payload -> payload.x },
            ByteBufCodecs.DOUBLE,
            { payload -> payload.y },
            ByteBufCodecs.DOUBLE,
            { payload -> payload.z },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.soundId },
            ByteBufCodecs.FLOAT,
            { payload -> payload.volume },
            ByteBufCodecs.FLOAT,
            { payload -> payload.pitch },
            ::SpeakerPlayPayload,
        )

        @JvmStatic
        fun handleOnClient(payload: SpeakerPlayPayload, context: IPayloadContext) {
            context.enqueueWork {
                LegacyScriptSoundManager.playAt(
                    payload.x,
                    payload.y,
                    payload.z,
                    payload.soundId,
                    payload.volume,
                    payload.pitch,
                )
            }
        }
    }
}

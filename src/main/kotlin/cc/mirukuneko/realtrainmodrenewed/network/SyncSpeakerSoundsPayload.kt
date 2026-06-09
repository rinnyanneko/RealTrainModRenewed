package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Server-to-client sync of speaker sound id/name mappings. */
@JvmRecord
data class SyncSpeakerSoundsPayload(
    val sounds: List<String>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SyncSpeakerSoundsPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "sync_speaker_sounds")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SyncSpeakerSoundsPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(512)),
            { payload -> payload.sounds },
            ::SyncSpeakerSoundsPayload,
        )

        @JvmStatic
        fun handleOnClient(payload: SyncSpeakerSoundsPayload, context: IPayloadContext) {
            context.enqueueWork {
                SpeakerSoundConfig.replaceAll(payload.sounds.toTypedArray())
            }
        }
    }
}

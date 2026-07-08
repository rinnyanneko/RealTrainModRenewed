package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.client.sound.LegacyScriptSoundManager
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

data class TrainSoundPayload(
    val trainEntityId: Int,
    val soundId: String,
    val volume: Float,
    val pitch: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<TrainSoundPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "train_sound")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, TrainSoundPayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            { payload -> payload.trainEntityId },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.soundId },
            ByteBufCodecs.FLOAT,
            { payload -> payload.volume },
            ByteBufCodecs.FLOAT,
            { payload -> payload.pitch },
            ::TrainSoundPayload,
        )

        @JvmStatic
        fun handleOnClient(payload: TrainSoundPayload, context: IPayloadContext) {
            context.enqueueWork {
                val minecraft = Minecraft.getInstance()
                val level = minecraft.level ?: return@enqueueWork
                if (payload.soundId.isBlank()) {
                    return@enqueueWork
                }
                val train = level.getEntity(payload.trainEntityId) as? TrainEntity ?: return@enqueueWork
                LegacyScriptSoundManager.playLegacyId(train, payload.soundId, payload.volume, payload.pitch, false)
            }
        }
    }
}

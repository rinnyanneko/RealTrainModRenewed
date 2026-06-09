package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.InstalledObjectBlockEntity
import cc.mirukuneko.realtrainmodrenewed.installedobject.SpeakerSoundConfig
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Arrays

/** Client-to-server speaker GUI configuration payload. */
@JvmRecord
data class ConfigureSpeakerPayload(
    val pos: BlockPos,
    val soundSlot: Int,
    val soundName: String,
    val range: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<ConfigureSpeakerPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "configure_speaker")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, ConfigureSpeakerPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.INT,
            { payload -> payload.soundSlot },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.soundName },
            ByteBufCodecs.INT,
            { payload -> payload.range },
            ::ConfigureSpeakerPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: ConfigureSpeakerPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                if (payload.soundSlot in 1..SpeakerSoundConfig.MAX_SOUND_ID) {
                    SpeakerSoundConfig.setSound(payload.soundSlot, payload.soundName, true)
                    val sync = SyncSpeakerSoundsPayload(Arrays.asList(*SpeakerSoundConfig.snapshot()))
                    for (target in player.level().server.playerList.players) {
                        PacketDistributor.sendToPlayer(target, sync)
                    }
                }
                val blockEntity = player.level().getBlockEntity(payload.pos)
                if (payload.range >= 1 && blockEntity is InstalledObjectBlockEntity && blockEntity.isSpeaker) {
                    blockEntity.setSpeakerRange(payload.range)
                }
            }
        }
    }
}

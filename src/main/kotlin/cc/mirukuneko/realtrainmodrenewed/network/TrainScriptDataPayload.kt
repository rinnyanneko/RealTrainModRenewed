package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import io.netty.buffer.ByteBuf
import net.minecraft.client.Minecraft
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.HashMap

@JvmRecord
data class TrainScriptDataPayload(
    val trainEntityId: Int,
    val data: Map<String, String>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<TrainScriptDataPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "train_script_data")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, TrainScriptDataPayload> = StreamCodec.of(
            { buf, payload ->
                ByteBufCodecs.INT.encode(buf, payload.trainEntityId)
                ByteBufCodecs.INT.encode(buf, payload.data.size)
                for ((key, value) in payload.data) {
                    ByteBufCodecs.STRING_UTF8.encode(buf, key)
                    ByteBufCodecs.STRING_UTF8.encode(buf, value)
                }
            },
            { buf ->
                val id = ByteBufCodecs.INT.decode(buf)
                val size = ByteBufCodecs.INT.decode(buf)
                val map = HashMap<String, String>(size)
                repeat(size) {
                    val key = ByteBufCodecs.STRING_UTF8.decode(buf)
                    val value = ByteBufCodecs.STRING_UTF8.decode(buf)
                    map[key] = value
                }
                TrainScriptDataPayload(id, map)
            },
        )

        @JvmStatic
        fun handleOnClient(payload: TrainScriptDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                val minecraft = Minecraft.getInstance()
                val level = minecraft.level ?: return@enqueueWork
                val train = level.getEntity(payload.trainEntityId) as? TrainEntity ?: return@enqueueWork
                train.applyScriptDataSync(payload.data)
            }
        }
    }
}

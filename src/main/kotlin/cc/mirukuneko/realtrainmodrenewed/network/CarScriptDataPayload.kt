package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.entity.CarEntity
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Client-to-server scriptData/DataMap sync for script-driven CarEntity state. */
@JvmRecord
data class CarScriptDataPayload(
    val entityId: Int,
    val key: String,
    val value: String,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<CarScriptDataPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "car_script_data")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, CarScriptDataPayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            { payload -> payload.entityId },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.key },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.value },
            ::CarScriptDataPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: CarScriptDataPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player()
                val car = player.level().getEntity(payload.entityId) as? CarEntity ?: return@enqueueWork
                car.setScriptDataValue(payload.key, payload.value)
            }
        }
    }
}

package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormation
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormationData
import cc.mirukuneko.realtrainmodrenewed.item.TrainVehicleItem
import cc.mirukuneko.realtrainmodrenewed.item.VehicleFormationItem
import io.netty.buffer.ByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.ArrayList

@JvmRecord
data class SaveFormationPayload(
    val name: String,
    val vehicleIds: List<String>,
    val reversedFlags: List<Boolean>,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SaveFormationPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "save_formation")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SaveFormationPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.name },
            ByteBufCodecs.collection({ ArrayList<String>() }, ByteBufCodecs.STRING_UTF8, 256),
            { payload -> payload.vehicleIds },
            ByteBufCodecs.collection({ ArrayList<Boolean>() }, ByteBufCodecs.BOOL, 256),
            { payload -> payload.reversedFlags },
            ::SaveFormationPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: SaveFormationPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player()
                val formation = TrainFormation()
                formation.name = payload.name

                for (vehicleId in payload.vehicleIds) {
                    if (vehicleId.isNotBlank()) {
                        formation.addVehicle(vehicleId)
                    }
                }

                for (hand in InteractionHand.entries) {
                    val stack = player.getItemInHand(hand)
                    if (stack.item is VehicleFormationItem || stack.item is TrainVehicleItem) {
                        TrainFormationData.setFormation(stack, formation)
                        break
                    }
                }
            }
        }
    }
}

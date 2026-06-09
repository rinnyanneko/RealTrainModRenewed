package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.compat.LegacyItemStackBridge
import cc.mirukuneko.realtrainmodrenewed.item.CarItem
import cc.mirukuneko.realtrainmodrenewed.item.ModelSelectableItem
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import cc.mirukuneko.realtrainmodrenewed.item.TrainItem
import cc.mirukuneko.realtrainmodrenewed.item.TrainVehicleItem
import io.netty.buffer.ByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.neoforged.neoforge.network.handling.IPayloadContext

@JvmRecord
data class SelectModelPayload(
    val modelId: String,
    val dataMapValue: String,
) : CustomPacketPayload {
    constructor(modelId: String) : this(modelId, "")

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SelectModelPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "select_model")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SelectModelPayload> = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.modelId },
            ByteBufCodecs.STRING_UTF8,
            { payload -> payload.dataMapValue },
            ::SelectModelPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: SelectModelPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player()
                val safeModelId = payload.modelId
                val safeDataMap = payload.dataMapValue

                for (hand in InteractionHand.entries) {
                    val stack = player.getItemInHand(hand)
                    if (stack.item is TrainVehicleItem) {
                        LegacyItemStackBridge.setSelectedModelData(stack, safeModelId, safeDataMap)
                        player.sendSystemMessage(
                            Component.literal("Selected model: $safeModelId. Now right-click on rail to spawn.")
                        )
                        break
                    }
                }

                for (hand in InteractionHand.entries) {
                    val stack = player.getItemInHand(hand)
                    val item = stack.item
                    if (item is RailItem || item is TrainItem || item is CarItem || item is ModelSelectableItem) {
                        LegacyItemStackBridge.setSelectedModelData(stack, safeModelId, safeDataMap)
                        break
                    }
                }
            }
        }
    }
}

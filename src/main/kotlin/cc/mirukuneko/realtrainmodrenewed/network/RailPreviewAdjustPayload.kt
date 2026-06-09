package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewedComponents
import cc.mirukuneko.realtrainmodrenewed.compat.NbtCompat
import cc.mirukuneko.realtrainmodrenewed.item.RailItem
import io.netty.buffer.ByteBuf
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

/** Sends a small rail preview endpoint offset from the client to the server. */
@JvmRecord
data class RailPreviewAdjustPayload(
    val dx: Int,
    val dy: Int,
    val dz: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<RailPreviewAdjustPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "rail_preview_adjust")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, RailPreviewAdjustPayload> = StreamCodec.composite(
            ByteBufCodecs.INT,
            { payload -> payload.dx },
            ByteBufCodecs.INT,
            { payload -> payload.dy },
            ByteBufCodecs.INT,
            { payload -> payload.dz },
            ::RailPreviewAdjustPayload,
        )

        /** Applies the preview adjustment to the rail item held by the sending player. */
        @JvmStatic
        fun handleOnServer(payload: RailPreviewAdjustPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player()
                for (hand in InteractionHand.entries) {
                    val stack = player.getItemInHand(hand)
                    if (stack.item is RailItem && apply(stack, payload.dx, payload.dy, payload.dz)) {
                        return@enqueueWork
                    }
                }
            }
        }

        /** Applies the preview adjustment to an item stack. */
        @JvmStatic
        fun apply(stack: ItemStack, dx: Int, dy: Int, dz: Int): Boolean {
            val tag = stack.get(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get()) ?: return false
            if (!tag.contains("X") || !tag.contains("Y") || !tag.contains("Z")) {
                return false
            }
            val copy: CompoundTag = tag.copy()
            copy.putInt("OffsetX", clampOffset(NbtCompat.getInt(copy, "OffsetX") + dx))
            copy.putInt("OffsetY", clampOffset(NbtCompat.getInt(copy, "OffsetY") + dy))
            copy.putInt("OffsetZ", clampOffset(NbtCompat.getInt(copy, "OffsetZ") + dz))
            stack.set(RealTrainModRenewedComponents.RAIL_PREVIEW_START.get(), copy)
            return true
        }

        private fun clampOffset(value: Int): Int = value.coerceIn(-64, 64)
    }
}

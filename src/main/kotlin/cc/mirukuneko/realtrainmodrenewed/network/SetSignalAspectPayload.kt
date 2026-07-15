// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalRemoteBlockEntity
import cc.mirukuneko.realtrainmodrenewed.signal.SignalAspect
import cc.mirukuneko.realtrainmodrenewed.signal.SignalNetworkSavedData
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

data class SetSignalAspectPayload(
    val pos: BlockPos,
    val aspectId: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<SetSignalAspectPayload> = CustomPacketPayload.Type(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "set_signal_aspect")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SetSignalAspectPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            { payload -> payload.pos },
            ByteBufCodecs.INT,
            { payload -> payload.aspectId },
            ::SetSignalAspectPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: SetSignalAspectPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                val blockEntity = player.level().getBlockEntity(payload.pos) as? SignalRemoteBlockEntity ?: return@enqueueWork
                val linkedChannel = blockEntity.linkedChannel
                if (linkedChannel <= 0) {
                    player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.signal.changer_unlinked"))
                    return@enqueueWork
                }
                val data = SignalNetworkSavedData.get(player.level())
                if (!data.hasChannel(linkedChannel)) {
                    player.sendOverlayMessage(Component.translatable("message.realtrainmodrenewed.signal.invalid_channel"))
                    return@enqueueWork
                }
                val aspect = SignalAspect.byId(payload.aspectId)
                data.setAspect(player.level().server, linkedChannel, aspect)
                player.sendOverlayMessage(Component.translatable(
                    "message.realtrainmodrenewed.signal.aspect_changed",
                    Component.translatable(aspect.translationKey),
                ))
            }
        }
    }
}

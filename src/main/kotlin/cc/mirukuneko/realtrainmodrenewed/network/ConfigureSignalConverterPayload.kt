// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.network

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalConverterBlockEntity
import cc.mirukuneko.realtrainmodrenewed.electric.SignalComparator
import io.netty.buffer.ByteBuf
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext

@JvmRecord
data class ConfigureSignalConverterPayload(
    val pos: BlockPos,
    val signalOnTrue: Int,
    val signalOnFalse: Int,
    val comparatorId: Int,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<ConfigureSignalConverterPayload>(
            Identifier.fromNamespaceAndPath(RealTrainModRenewed.MODID, "configure_signal_converter")
        )

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, ConfigureSignalConverterPayload> = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            ConfigureSignalConverterPayload::pos,
            ByteBufCodecs.INT,
            ConfigureSignalConverterPayload::signalOnTrue,
            ByteBufCodecs.INT,
            ConfigureSignalConverterPayload::signalOnFalse,
            ByteBufCodecs.INT,
            ConfigureSignalConverterPayload::comparatorId,
            ::ConfigureSignalConverterPayload,
        )

        @JvmStatic
        fun handleOnServer(payload: ConfigureSignalConverterPayload, context: IPayloadContext) {
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                if (player.blockPosition().distSqr(payload.pos) > 64.0) return@enqueueWork
                val blockEntity = player.level().getBlockEntity(payload.pos) as? SignalConverterBlockEntity
                    ?: return@enqueueWork
                blockEntity.setSignalProperties(
                    payload.signalOnTrue,
                    payload.signalOnFalse,
                    SignalComparator.byId(payload.comparatorId),
                )
            }
        }
    }
}

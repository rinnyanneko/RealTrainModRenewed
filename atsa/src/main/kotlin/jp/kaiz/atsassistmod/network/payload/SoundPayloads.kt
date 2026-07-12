// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.network.payload

import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** Server -> client sound sequence playback (mirrors PacketPlaySounds / PacketPlaySoundsEntity). */
object SoundPayloads {
    @JvmStatic
    private fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, path)

    @JvmStatic
    private fun writeOrders(buf: RegistryFriendlyByteBuf, orders: List<String?>) {
        buf.writeVarInt(orders.size)
        for (order in orders) {
            buf.writeUtf(order ?: "")
        }
    }

    @JvmStatic
    private fun readOrders(buf: RegistryFriendlyByteBuf): List<String> {
        val count = buf.readVarInt()
        val list = ArrayList<String>(count)
        repeat(count) {
            list.add(buf.readUtf())
        }
        return list
    }

    /** Plays a sound sequence at one or more positions (anchored to an IFTTT block). */
    data class PlaySoundsAt(
        val positions: List<IntArray>,
        val orders: List<String>,
        val volume: Float,
    ) : CustomPacketPayload {
        fun positions(): List<IntArray> = positions
        fun orders(): List<String> = orders
        fun volume(): Float = volume

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<PlaySoundsAt> = CustomPacketPayload.Type(id("play_sounds_at"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlaySoundsAt> = StreamCodec.of(
                { buf, payload ->
                    buf.writeVarInt(payload.positions.size)
                    for (pos in payload.positions) {
                        buf.writeVarInt(pos[0])
                        buf.writeVarInt(pos[1])
                        buf.writeVarInt(pos[2])
                    }
                    writeOrders(buf, payload.orders)
                    buf.writeFloat(payload.volume)
                },
                { buf ->
                    val count = buf.readVarInt()
                    val positions = ArrayList<IntArray>(count)
                    repeat(count) {
                        positions.add(intArrayOf(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()))
                    }
                    PlaySoundsAt(positions, readOrders(buf), buf.readFloat())
                },
            )
        }
    }

    /** Plays a sound sequence following an entity. */
    data class PlaySoundsEntity(
        val entityId: Int,
        val orders: List<String>,
        val volume: Float,
    ) : CustomPacketPayload {
        fun entityId(): Int = entityId
        fun orders(): List<String> = orders
        fun volume(): Float = volume

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<PlaySoundsEntity> = CustomPacketPayload.Type(id("play_sounds_entity"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlaySoundsEntity> = StreamCodec.of(
                { buf, payload ->
                    buf.writeVarInt(payload.entityId)
                    writeOrders(buf, payload.orders)
                    buf.writeFloat(payload.volume)
                },
                { buf -> PlaySoundsEntity(buf.readVarInt(), readOrders(buf), buf.readFloat()) },
            )
        }
    }
}

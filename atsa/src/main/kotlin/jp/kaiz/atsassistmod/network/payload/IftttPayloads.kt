// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.network.payload

import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/** Client -> server save of an IFTTT block's rule lists (serialized containers). */
class IftttPayloads {
    data class SaveIfttt(
        val pos: BlockPos,
        val anyMatch: Boolean,
        val thisData: List<ByteArray>,
        val thatData: List<ByteArray>,
    ) : CustomPacketPayload {
        fun pos(): BlockPos = pos
        fun anyMatch(): Boolean = anyMatch
        fun thisData(): List<ByteArray> = thisData
        fun thatData(): List<ByteArray> = thatData

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<SaveIfttt> =
                CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "save_ifttt"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SaveIfttt> = StreamCodec.of(
                { buf, payload ->
                    buf.writeBlockPos(payload.pos)
                    buf.writeBoolean(payload.anyMatch)
                    writeList(buf, payload.thisData)
                    writeList(buf, payload.thatData)
                },
                { buf -> SaveIfttt(buf.readBlockPos(), buf.readBoolean(), readList(buf), readList(buf)) },
            )

            @JvmStatic
            private fun writeList(buf: RegistryFriendlyByteBuf, list: List<ByteArray>) {
                buf.writeVarInt(list.size)
                for (bytes in list) {
                    buf.writeByteArray(bytes)
                }
            }

            @JvmStatic
            private fun readList(buf: RegistryFriendlyByteBuf): List<ByteArray> {
                val count = buf.readVarInt()
                val list = ArrayList<ByteArray>(count)
                repeat(count) {
                    list.add(buf.readByteArray())
                }
                return list
            }
        }
    }
}

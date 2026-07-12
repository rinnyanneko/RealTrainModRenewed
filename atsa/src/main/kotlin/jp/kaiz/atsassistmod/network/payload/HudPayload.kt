// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.network.payload

import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Server -> client HUD state for a formation (mirrors PacketTrainControllerToClient).
 * updateType == 0 removes the entry, updateType == 1 updates it.
 */
data class HudPayload(
    val updateType: Int,
    val formationId: Long,
    val ato: Boolean,
    val tasc: Boolean,
    val tpType: Int,
    val atoSpeed: Int,
    val tascDistance: Int,
    val atcSpeed: Int,
    val tpLimit: Int,
    val manual: Boolean,
) : CustomPacketPayload {
    fun updateType(): Int = updateType
    fun formationId(): Long = formationId
    fun ato(): Boolean = ato
    fun tasc(): Boolean = tasc
    fun tpType(): Int = tpType
    fun atoSpeed(): Int = atoSpeed
    fun tascDistance(): Int = tascDistance
    fun atcSpeed(): Int = atcSpeed
    fun tpLimit(): Int = tpLimit
    fun manual(): Boolean = manual

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE: CustomPacketPayload.Type<HudPayload> =
            CustomPacketPayload.Type(Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, "train_hud"))

        @JvmField
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, HudPayload> = StreamCodec.of(
            { buf, payload ->
                buf.writeVarInt(payload.updateType)
                buf.writeVarLong(payload.formationId)
                buf.writeBoolean(payload.ato)
                buf.writeBoolean(payload.tasc)
                buf.writeVarInt(payload.tpType)
                buf.writeVarInt(payload.atoSpeed)
                buf.writeVarInt(payload.tascDistance)
                buf.writeVarInt(payload.atcSpeed)
                buf.writeVarInt(payload.tpLimit)
                buf.writeBoolean(payload.manual)
            },
            { buf ->
                HudPayload(
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                )
            },
        )

        @JvmStatic
        fun remove(formationId: Long): HudPayload =
            HudPayload(0, formationId, false, false, 0, 0, 0, 0, 0, false)
    }
}

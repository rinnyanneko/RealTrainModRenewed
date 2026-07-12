// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.network.payload

import jp.kaiz.atsassistmod.ATSAssistMod
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier

/**
 * Client -> server control payloads (driver SW / ground-unit configuration).
 * Mirrors the original packets while keeping Java record-style accessors.
 */
object ControlPayloads {
    @JvmStatic
    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, path)

    data class SetNotchController(val notch: Int) : CustomPacketPayload {
        fun notch(): Int = notch

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<SetNotchController> = CustomPacketPayload.Type(id("set_notch_controller"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SetNotchController> = StreamCodec.of(
                { buf, payload -> buf.writeVarInt(payload.notch) },
                { buf -> SetNotchController(buf.readVarInt()) },
            )
        }
    }

    data class SetTrainState(val stateId: Int, val value: Int) : CustomPacketPayload {
        fun stateId(): Int = stateId
        fun value(): Int = value

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<SetTrainState> = CustomPacketPayload.Type(id("set_train_state"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SetTrainState> = StreamCodec.of(
                { buf, payload ->
                    buf.writeVarInt(payload.stateId)
                    buf.writeVarInt(payload.value)
                },
                { buf -> SetTrainState(buf.readVarInt(), buf.readVarInt()) },
            )
        }
    }

    class EmergencyBrake : CustomPacketPayload {
        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val INSTANCE = EmergencyBrake()

            @JvmField
            val TYPE: CustomPacketPayload.Type<EmergencyBrake> = CustomPacketPayload.Type(id("emergency_brake"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, EmergencyBrake> = StreamCodec.unit(INSTANCE)
        }
    }

    data class ManualDrive(val manual: Boolean) : CustomPacketPayload {
        fun manual(): Boolean = manual

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<ManualDrive> = CustomPacketPayload.Type(id("manual_drive"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, ManualDrive> = StreamCodec.of(
                { buf, payload -> buf.writeBoolean(payload.manual) },
                { buf -> ManualDrive(buf.readBoolean()) },
            )
        }
    }

    data class TrainDriveMode(val mode: Int) : CustomPacketPayload {
        fun mode(): Int = mode

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<TrainDriveMode> = CustomPacketPayload.Type(id("train_drive_mode"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, TrainDriveMode> = StreamCodec.of(
                { buf, payload -> buf.writeVarInt(payload.mode) },
                { buf -> TrainDriveMode(buf.readVarInt()) },
            )
        }
    }

    data class TrainProtectionSetter(val typeId: Int) : CustomPacketPayload {
        fun typeId(): Int = typeId

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<TrainProtectionSetter> =
                CustomPacketPayload.Type(id("train_protection_setter"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, TrainProtectionSetter> = StreamCodec.of(
                { buf, payload -> buf.writeVarInt(payload.typeId) },
                { buf -> TrainProtectionSetter(buf.readVarInt()) },
            )
        }
    }

    /** Sets the ground-unit variant (block state TYPE), like PacketGroundUnitTileInit (id>=0). */
    data class SetGroundUnitType(val pos: BlockPos, val typeId: Int) : CustomPacketPayload {
        fun pos(): BlockPos = pos
        fun typeId(): Int = typeId

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<SetGroundUnitType> = CustomPacketPayload.Type(id("set_ground_unit_type"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SetGroundUnitType> = StreamCodec.of(
                { buf, payload ->
                    buf.writeBlockPos(payload.pos)
                    buf.writeVarInt(payload.typeId)
                },
                { buf -> SetGroundUnitType(buf.readBlockPos(), buf.readVarInt()) },
            )
        }
    }

    /** Saves all editable ground-unit fields (superset of the original per-type packet). */
    data class SaveGroundUnit(
        val pos: BlockPos,
        val linkRedstone: Boolean,
        val speed: Int,
        val distance: Double,
        val autoBrake: Boolean,
        val useTrainDistance: Boolean,
        val states: ByteArray,
        val tpType: Int,
    ) : CustomPacketPayload {
        fun pos(): BlockPos = pos
        fun linkRedstone(): Boolean = linkRedstone
        fun speed(): Int = speed
        fun distance(): Double = distance
        fun autoBrake(): Boolean = autoBrake
        fun useTrainDistance(): Boolean = useTrainDistance
        fun states(): ByteArray = states
        fun tpType(): Int = tpType

        override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

        companion object {
            @JvmField
            val TYPE: CustomPacketPayload.Type<SaveGroundUnit> = CustomPacketPayload.Type(id("save_ground_unit"))

            @JvmField
            val CODEC: StreamCodec<RegistryFriendlyByteBuf, SaveGroundUnit> = StreamCodec.of(
                { buf, payload ->
                    buf.writeBlockPos(payload.pos)
                    buf.writeBoolean(payload.linkRedstone)
                    buf.writeVarInt(payload.speed)
                    buf.writeDouble(payload.distance)
                    buf.writeBoolean(payload.autoBrake)
                    buf.writeBoolean(payload.useTrainDistance)
                    buf.writeByteArray(payload.states)
                    buf.writeVarInt(payload.tpType)
                },
                { buf ->
                    SaveGroundUnit(
                        buf.readBlockPos(),
                        buf.readBoolean(),
                        buf.readVarInt(),
                        buf.readDouble(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readByteArray(),
                        buf.readVarInt(),
                    )
                },
            )
        }
    }
}

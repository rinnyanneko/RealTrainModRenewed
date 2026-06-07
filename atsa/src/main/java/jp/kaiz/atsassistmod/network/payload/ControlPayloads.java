package jp.kaiz.atsassistmod.network.payload;

import jp.kaiz.atsassistmod.ATSAssistMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server control payloads (driver SW / ground-unit configuration).
 * Mirrors the original {@code PacketSetNotchController}, {@code PacketSetTrainState},
 * {@code PacketEmergencyBrake}, {@code PacketManualDrive}, {@code PacketTrainDriveMode},
 * {@code PacketTrainProtectionSetter}, {@code PacketGroundUnitTile(Init)}.
 */
public final class ControlPayloads {
    private ControlPayloads() {}

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ATSAssistMod.MODID, path);
    }

    // ---- driver SW (player riding the control car) ----
    public record SetNotchController(int notch) implements CustomPacketPayload {
        public static final Type<SetNotchController> TYPE = new Type<>(id("set_notch_controller"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetNotchController> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, SetNotchController::notch, SetNotchController::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SetTrainState(int stateId, int value) implements CustomPacketPayload {
        public static final Type<SetTrainState> TYPE = new Type<>(id("set_train_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetTrainState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SetTrainState::stateId,
                ByteBufCodecs.VAR_INT, SetTrainState::value,
                SetTrainState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EmergencyBrake() implements CustomPacketPayload {
        public static final EmergencyBrake INSTANCE = new EmergencyBrake();
        public static final Type<EmergencyBrake> TYPE = new Type<>(id("emergency_brake"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EmergencyBrake> CODEC =
                StreamCodec.unit(INSTANCE);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ManualDrive(boolean manual) implements CustomPacketPayload {
        public static final Type<ManualDrive> TYPE = new Type<>(id("manual_drive"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ManualDrive> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, ManualDrive::manual, ManualDrive::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TrainDriveMode(int mode) implements CustomPacketPayload {
        public static final Type<TrainDriveMode> TYPE = new Type<>(id("train_drive_mode"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TrainDriveMode> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TrainDriveMode::mode, TrainDriveMode::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record TrainProtectionSetter(int typeId) implements CustomPacketPayload {
        public static final Type<TrainProtectionSetter> TYPE = new Type<>(id("train_protection_setter"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TrainProtectionSetter> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, TrainProtectionSetter::typeId, TrainProtectionSetter::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---- ground unit configuration ----
    /** Sets the ground-unit variant (block state TYPE), like PacketGroundUnitTileInit (id>=0). */
    public record SetGroundUnitType(BlockPos pos, int typeId) implements CustomPacketPayload {
        public static final Type<SetGroundUnitType> TYPE = new Type<>(id("set_ground_unit_type"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetGroundUnitType> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SetGroundUnitType::pos,
                ByteBufCodecs.VAR_INT, SetGroundUnitType::typeId,
                SetGroundUnitType::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Saves all editable ground-unit fields (superset of the original per-type packet). */
    public record SaveGroundUnit(BlockPos pos, boolean linkRedstone, int speed, double distance,
                                 boolean autoBrake, boolean useTrainDistance, byte[] states, int tpType)
            implements CustomPacketPayload {
        public static final Type<SaveGroundUnit> TYPE = new Type<>(id("save_ground_unit"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SaveGroundUnit> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBlockPos(p.pos);
                    buf.writeBoolean(p.linkRedstone);
                    buf.writeVarInt(p.speed);
                    buf.writeDouble(p.distance);
                    buf.writeBoolean(p.autoBrake);
                    buf.writeBoolean(p.useTrainDistance);
                    buf.writeByteArray(p.states);
                    buf.writeVarInt(p.tpType);
                },
                buf -> new SaveGroundUnit(
                        buf.readBlockPos(), buf.readBoolean(), buf.readVarInt(), buf.readDouble(),
                        buf.readBoolean(), buf.readBoolean(), buf.readByteArray(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}

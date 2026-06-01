package com.portofino.realtrainmodunofficial.compat.atsassist.blockentity;

import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlocks;
import com.portofino.realtrainmodunofficial.RealTrainModUnofficialBlockEntities;
import com.portofino.realtrainmodunofficial.entity.TrainEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class AtsaSimpleBlockEntity extends BlockEntity {
    private String data = "";
    private String condition = "train";
    private String action = "redstone=15";
    private String command = "";
    private String announce = "";
    private boolean anyMatch;
    private boolean triggered;
    private int redstoneOutput;
    private int cooldown;

    public AtsaSimpleBlockEntity(BlockPos pos, BlockState blockState) {
        super(RealTrainModUnofficialBlockEntities.ATSA_SIMPLE.get(), pos, blockState);
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, AtsaSimpleBlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel) || (serverLevel.getGameTime() + pos.asLong()) % 5L != 0L) {
            return;
        }
        blockEntity.tickServer(serverLevel, pos, state);
    }

    private void tickServer(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        List<TrainEntity> trains = level.getEntitiesOfClass(TrainEntity.class, new AABB(pos).inflate(2.0D, 3.0D, 2.0D), TrainEntity::isAlive);
        TrainEntity train = trains.isEmpty() ? null : trains.get(0);
        if (block == RealTrainModUnofficialBlocks.ATSA_STATION_ANNOUNCE.get()) {
            tickStationAnnounce(level, train);
        } else if (block == RealTrainModUnofficialBlocks.ATSA_IFTTT.get()) {
            tickIfttt(level, train);
        }
    }

    private void tickStationAnnounce(ServerLevel level, TrainEntity train) {
        if (cooldown > 0) {
            cooldown--;
        }
        boolean hasTrain = train != null;
        redstoneOutput = hasTrain ? 15 : 0;
        if (hasTrain && cooldown == 0 && !announce.isBlank()) {
            String message = announce.replace("{train}", train.getVehicleId()).replace("{id}", Integer.toString(train.getId()));
            for (net.minecraft.server.level.ServerPlayer player : level.players()) {
                if (player.distanceToSqr(train) < 4096.0D) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(message), false);
                }
            }
            cooldown = 80;
        }
        setChanged();
    }

    private void tickIfttt(ServerLevel level, TrainEntity train) {
        boolean matched = matchesCondition(level, train);
        if (matched) {
            runAction(level, train, !triggered);
            triggered = true;
        } else if (triggered) {
            redstoneOutput = 0;
            triggered = false;
        }
        setChanged();
    }

    private boolean matchesCondition(ServerLevel level, TrainEntity train) {
        String[] conditions = condition.split("\\R+|;");
        boolean saw = false;
        boolean result = anyMatch ? false : true;
        for (String raw : conditions) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            saw = true;
            boolean matched = matchOne(level, train, entry);
            if (anyMatch) {
                result |= matched;
            } else {
                result &= matched;
            }
        }
        return saw && result;
    }

    private boolean matchOne(ServerLevel level, TrainEntity train, String entry) {
        if ("train".equalsIgnoreCase(entry)) {
            return train != null;
        }
        if ("redstone".equalsIgnoreCase(entry)) {
            return level.hasNeighborSignal(worldPosition);
        }
        if ("no_redstone".equalsIgnoreCase(entry)) {
            return !level.hasNeighborSignal(worldPosition);
        }
        if (entry.startsWith("speed")) {
            return train != null && compare(Math.abs(train.getSpeed()) * 72.0F, entry.substring("speed".length()).trim());
        }
        if (entry.startsWith("notch")) {
            return train != null && compare(train.getNotch(), entry.substring("notch".length()).trim());
        }
        if (entry.startsWith("data:")) {
            if (train == null) {
                return false;
            }
            String body = entry.substring("data:".length());
            int eq = body.indexOf('=');
            if (eq < 0) {
                return !train.getScriptDataValue(body).isBlank();
            }
            return train.getScriptDataValue(body.substring(0, eq)).equals(body.substring(eq + 1));
        }
        return false;
    }

    private static boolean compare(double current, String expression) {
        String expr = expression.trim();
        try {
            if (expr.startsWith(">=")) return current >= Double.parseDouble(expr.substring(2).trim());
            if (expr.startsWith("<=")) return current <= Double.parseDouble(expr.substring(2).trim());
            if (expr.startsWith("!=")) return current != Double.parseDouble(expr.substring(2).trim());
            if (expr.startsWith(">")) return current > Double.parseDouble(expr.substring(1).trim());
            if (expr.startsWith("<")) return current < Double.parseDouble(expr.substring(1).trim());
            if (expr.startsWith("=")) return current == Double.parseDouble(expr.substring(1).trim());
            return current == Double.parseDouble(expr);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private void runAction(ServerLevel level, TrainEntity train, boolean first) {
        for (String raw : action.split("\\R+|;")) {
            String entry = raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            if (entry.startsWith("redstone=")) {
                redstoneOutput = parseInt(entry.substring("redstone=".length()), redstoneOutput);
                level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            } else if (entry.startsWith("notch=") && train != null) {
                train.setNotch(parseInt(entry.substring("notch=".length()), train.getNotch()));
            } else if (entry.startsWith("state:") && train != null) {
                setTrainState(train, entry.substring("state:".length()));
            } else if (entry.startsWith("data:") && train != null) {
                setTrainData(train, entry.substring("data:".length()));
            } else if (entry.startsWith("command=") && first) {
                String commandText = entry.substring("command=".length()).trim();
                if (!commandText.isBlank()) {
                    level.getServer().getCommands().performPrefixedCommand(level.getServer().createCommandSourceStack(), commandText);
                }
            } else if (entry.startsWith("sound=") && first) {
                playSound(level, train, entry.substring("sound=".length()).trim());
            } else if (entry.startsWith("setblock=") && first) {
                setRelativeBlock(level, entry.substring("setblock=".length()).trim());
            }
        }
    }

    private void playSound(ServerLevel level, TrainEntity train, String soundId) {
        ResourceLocation location = ResourceLocation.tryParse(soundId);
        if (location == null) {
            return;
        }
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(location);
        double x = train == null ? worldPosition.getX() + 0.5D : train.getX();
        double y = train == null ? worldPosition.getY() + 0.5D : train.getY();
        double z = train == null ? worldPosition.getZ() + 0.5D : train.getZ();
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private void setRelativeBlock(ServerLevel level, String spec) {
        String[] parts = spec.split(",");
        if (parts.length < 4) {
            return;
        }
        int dx = parseInt(parts[0], 0);
        int dy = parseInt(parts[1], 0);
        int dz = parseInt(parts[2], 0);
        ResourceLocation blockId = ResourceLocation.tryParse(parts[3].trim());
        if (blockId == null) {
            return;
        }
        Block block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == Blocks.AIR && !"minecraft:air".equals(blockId.toString())) {
            return;
        }
        level.setBlock(worldPosition.offset(dx, dy, dz), block.defaultBlockState(), 3);
    }

    private static void setTrainState(TrainEntity train, String body) {
        int eq = body.indexOf('=');
        if (eq < 0) {
            return;
        }
        int state = parseInt(body.substring(0, eq), -1);
        float value = (float) parseDouble(body.substring(eq + 1), 0.0D);
        if (state >= 0) {
            train.syncVehicleState(state, value);
        }
    }

    private static void setTrainData(TrainEntity train, String body) {
        int eq = body.indexOf('=');
        if (eq < 0) {
            return;
        }
        train.applyScriptDataSync(java.util.Map.of(body.substring(0, eq), body.substring(eq + 1)));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double parseDouble(String value, double fallback) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Data", data);
        tag.putString("Condition", condition);
        tag.putString("Action", action);
        tag.putString("Command", command);
        tag.putString("Announce", announce);
        tag.putBoolean("AnyMatch", anyMatch);
        tag.putBoolean("Triggered", triggered);
        tag.putInt("RedstoneOutput", redstoneOutput);
        tag.putInt("Cooldown", cooldown);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        data = tag.getString("Data");
        if (tag.contains("Condition")) condition = tag.getString("Condition");
        if (tag.contains("Action")) action = tag.getString("Action");
        command = tag.getString("Command");
        announce = tag.getString("Announce");
        anyMatch = tag.getBoolean("AnyMatch");
        triggered = tag.getBoolean("Triggered");
        redstoneOutput = tag.getInt("RedstoneOutput");
        cooldown = Math.max(0, tag.getInt("Cooldown"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public String getData() {
        return data;
    }

    public String getCondition() {
        return condition;
    }

    public String getAction() {
        return action;
    }

    public String getAnnounce() {
        return announce;
    }

    public boolean isAnyMatch() {
        return anyMatch;
    }

    public int getRedstoneOutput() {
        return redstoneOutput;
    }

    public void configure(String condition, String action, String announce, boolean anyMatch) {
        this.condition = condition == null || condition.isBlank() ? "train" : condition;
        this.action = action == null || action.isBlank() ? "redstone=15" : action;
        this.announce = announce == null ? "" : announce;
        this.anyMatch = anyMatch;
        setChanged();
    }
}

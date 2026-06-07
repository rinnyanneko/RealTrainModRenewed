package cc.mirukuneko.realtrainmodrenewed;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock;
import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = RealTrainModRenewed.MODID)
public final class TrainCommands {
    private TrainCommands() {
    }

    @SubscribeEvent
    public static void onCommandsRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
            Commands.literal("del")
                .then(Commands.literal("train")
                    .executes(context -> executeDeleteTrain(context.getSource()))
                )
        );

        dispatcher.register(
            Commands.literal("rtm")
                .then(Commands.literal("delAlltrain")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .executes(context -> executeDeleteTrain(context.getSource()))
                )
                .then(Commands.literal("flyspeed")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.argument("speed", IntegerArgumentType.integer(1, 10))
                        .executes(context -> executeSetFlySpeed(
                            context.getSource(),
                            IntegerArgumentType.getInteger(context, "speed")
                        ))
                    )
                )
        );
    }

    private static int executeDeleteTrain(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        int removedCount = 0;
        TrainEntity.clearCouplingModes();

        for (ServerLevel level : server.getAllLevels()) {
            removedCount += removeTrainEntities(level);
            removeBogieEntities(level);
            removeRailCollisionBlocks(level);
        }

        int finalRemovedCount = removedCount;
        source.sendSuccess(() -> Component.literal("電車を " + finalRemovedCount + " 両削除しました。残って見える場合はワールドを開き直してください。"), true);
        return removedCount;
    }

    private static int executeSetFlySpeed(CommandSourceStack source, int speed) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        float normalizedSpeed = 0.05F * speed;
        player.getAbilities().setFlyingSpeed(normalizedSpeed);
        player.onUpdateAbilities();
        source.sendSuccess(() -> Component.literal("飛行速度を " + speed + " に設定しました。"), false);
        return speed;
    }

    private static int removeTrainEntities(ServerLevel level) {
        AABB worldAABB = new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D);
        List<TrainEntity> trains = new ArrayList<>(level.getEntitiesOfClass(TrainEntity.class, worldAABB, entity -> true));
        try {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TrainEntity train && !trains.contains(train)) {
                    trains.add(train);
                }
            }
        } catch (Exception ignored) {
        }
        for (TrainEntity train : trains) {
            train.forceDiscardTrain();
        }
        return trains.size();
    }

    private static void removeBogieEntities(ServerLevel level) {
        AABB worldAABB = new AABB(-3.0E7D, -2048.0D, -3.0E7D, 3.0E7D, 4096.0D, 3.0E7D);
        List<TrainBogieEntity> bogies = new ArrayList<>(level.getEntitiesOfClass(TrainBogieEntity.class, worldAABB, entity -> true));
        try {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof TrainBogieEntity bogie && !bogies.contains(bogie)) {
                    bogies.add(bogie);
                }
            }
        } catch (Exception ignored) {
        }
        for (TrainBogieEntity bogie : bogies) {
            bogie.discard();
        }
    }

    private static void removeRailCollisionBlocks(ServerLevel level) {
        if (!(level.getChunkSource() instanceof ServerChunkCache cache)) {
            return;
        }

        try {
            java.lang.reflect.Field field = ServerChunkCache.class.getDeclaredField("chunkMap");
            field.setAccessible(true);
            Object chunkMap = field.get(cache);
            java.lang.reflect.Method method = chunkMap.getClass().getMethod("getChunks");
            Iterable<?> chunks = (Iterable<?>) method.invoke(chunkMap);

            for (Object holderObject : chunks) {
                if (!(holderObject instanceof ChunkHolder holder)) {
                    continue;
                }
                Optional<ChunkAccess> optional = Optional.ofNullable(holder.getLatestChunk());
                if (optional.isEmpty() || !(optional.get() instanceof LevelChunk chunk)) {
                    continue;
                }

                List<BlockPos> blockPositions = new ArrayList<>(chunk.getBlockEntities().keySet());
                for (BlockPos pos : blockPositions) {
                    BlockState blockState = chunk.getBlockState(pos);
                    if (blockState.getBlock() instanceof RailCollisionBlock) {
                        level.removeBlock(pos, false);
                    } else if (blockState.getBlock() instanceof LargeRailCoreBlock) {
                        level.removeBlock(pos, false);
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            // If reflection fails, skip removing block entities rather than crashing.
        }
    }
}

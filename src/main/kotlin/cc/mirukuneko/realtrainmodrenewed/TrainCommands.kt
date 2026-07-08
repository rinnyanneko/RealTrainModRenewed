// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import cc.mirukuneko.realtrainmodrenewed.block.LargeRailCoreBlock
import cc.mirukuneko.realtrainmodrenewed.block.RailCollisionBlock
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ChunkHolder
import net.minecraft.server.level.ServerChunkCache
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.Permissions
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.AABB
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.RegisterCommandsEvent
import java.util.Optional

@EventBusSubscriber(modid = RealTrainModRenewed.MODID)
object TrainCommands {
    @SubscribeEvent
    @JvmStatic
    fun onCommandsRegister(event: RegisterCommandsEvent) {
        val dispatcher = event.dispatcher

        dispatcher.register(
            Commands.literal("del")
                .then(
                    Commands.literal("train")
                        .executes { context -> executeDeleteTrain(context.source) },
                ),
        )

        dispatcher.register(
            Commands.literal("rtm")
                .then(
                    Commands.literal("delAlltrain")
                        .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                        .executes { context -> executeDeleteTrain(context.source) },
                )
                .then(
                    Commands.literal("flyspeed")
                        .requires { source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) }
                        .then(
                            Commands.argument("speed", IntegerArgumentType.integer(1, 10))
                                .executes { context ->
                                    executeSetFlySpeed(
                                        context.source,
                                        IntegerArgumentType.getInteger(context, "speed"),
                                    )
                                },
                        ),
                ),
        )
    }

    private fun executeDeleteTrain(source: CommandSourceStack): Int {
        val server = source.server
        var removedCount = 0
        TrainEntity.clearCouplingModes()

        for (level in server.allLevels) {
            removedCount += removeTrainEntities(level)
            removeBogieEntities(level)
            removeRailCollisionBlocks(level)
        }

        val finalRemovedCount = removedCount
        source.sendSuccess(
            { Component.literal("電車を $finalRemovedCount 両削除しました。残って見える場合はワールドを開き直してください。") },
            true,
        )
        return removedCount
    }

    @Throws(CommandSyntaxException::class)
    private fun executeSetFlySpeed(source: CommandSourceStack, speed: Int): Int {
        val player = source.playerOrException
        val normalizedSpeed = 0.05F * speed
        player.abilities.setFlyingSpeed(normalizedSpeed)
        player.onUpdateAbilities()
        source.sendSuccess({ Component.literal("飛行速度を $speed に設定しました。") }, false)
        return speed
    }

    private fun removeTrainEntities(level: ServerLevel): Int {
        val worldAABB = AABB(-3.0E7, -2048.0, -3.0E7, 3.0E7, 4096.0, 3.0E7)
        val trains = ArrayList(level.getEntitiesOfClass(TrainEntity::class.java, worldAABB) { true })
        try {
            for (entity: Entity in level.allEntities) {
                if (entity is TrainEntity && !trains.contains(entity)) {
                    trains.add(entity)
                }
            }
        } catch (_: Exception) {
        }
        for (train in trains) {
            train.forceDiscardTrain()
        }
        return trains.size
    }

    private fun removeBogieEntities(level: ServerLevel) {
        val worldAABB = AABB(-3.0E7, -2048.0, -3.0E7, 3.0E7, 4096.0, 3.0E7)
        val bogies = ArrayList(level.getEntitiesOfClass(TrainBogieEntity::class.java, worldAABB) { true })
        try {
            for (entity: Entity in level.allEntities) {
                if (entity is TrainBogieEntity && !bogies.contains(entity)) {
                    bogies.add(entity)
                }
            }
        } catch (_: Exception) {
        }
        for (bogie in bogies) {
            bogie.discard()
        }
    }

    private fun removeRailCollisionBlocks(level: ServerLevel) {
        val cache: ServerChunkCache = level.chunkSource

        try {
            val field = ServerChunkCache::class.java.getDeclaredField("chunkMap")
            field.isAccessible = true
            val chunkMap = field.get(cache)
            val method = chunkMap.javaClass.getMethod("getChunks")
            val chunks = method.invoke(chunkMap) as Iterable<*>

            for (holderObject in chunks) {
                if (holderObject !is ChunkHolder) {
                    continue
                }
                val optional: Optional<ChunkAccess> = Optional.ofNullable(holderObject.getLatestChunk())
                if (optional.isEmpty || optional.get() !is LevelChunk) {
                    continue
                }

                val chunk = optional.get() as LevelChunk
                val blockPositions = ArrayList<BlockPos>(chunk.blockEntities.keys)
                for (pos in blockPositions) {
                    val blockState: BlockState = chunk.getBlockState(pos)
                    if (blockState.block is RailCollisionBlock) {
                        level.removeBlock(pos, false)
                    } else if (blockState.block is LargeRailCoreBlock) {
                        level.removeBlock(pos, false)
                    }
                }
            }
        } catch (_: ReflectiveOperationException) {
            // If reflection fails, skip removing block entities rather than crashing.
        }
    }
}

// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.ifttt

import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonTypeInfo
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import jp.kaiz.atsassistmod.network.ATSAModNet
import jp.kaiz.atsassistmod.network.payload.SoundPayloads
import jp.kaiz.atsassistmod.rtm.RtmTrains
import jp.kaiz.atsassistmod.util.CardinalDirection
import jp.kaiz.atsassistmod.util.ComparisonManager
import jp.kaiz.atsassistmod.util.DataType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.PermissionSet
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.io.Serializable
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * IFTTT rule container. Jackson polymorphic by class name, so the nested class
 * structure mirrors the Java port.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class IFTTTContainer : Serializable {
    private var once = false

    abstract fun getType(): IFTTTType.IFTTTEnumBase?

    open fun getTitle(): String = getType()?.getTranslationKey() ?: ""

    abstract fun getExplanation(): Array<String>

    abstract fun setFromGui(gui: IftttEditView)

    fun setOnce(once: Boolean) {
        this.once = once
    }

    fun isOnce(): Boolean = once

    abstract class This : IFTTTContainer() {
        abstract fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean

        abstract class Minecraft {
            class RedStoneInput : This() {
                enum class ModeType(@JvmField val symbol: String, @JvmField val needStr: Boolean) {
                    ON("ON", false),
                    OFF("OFF", false),
                    EQUAL("==", true),
                    GREATER_THAN(">", true),
                    GREATER_EQUAL(">=", true),
                    LESS_THAN("<", true),
                    LESS_EQUAL("<=", true),
                    NOT_EQUAL("!=", true),
                }

                var value = 0
                var mode = ModeType.ON

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.Minecraft.RedStoneInput

                override fun getExplanation(): Array<String> =
                    arrayOf("RSInput" + mode.symbol + if (mode.needStr) value else "")

                override fun setFromGui(gui: IftttEditView) {
                    value = gui.getTextFieldInt(0)
                }

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean {
                    val power = tile.level!!.getBestNeighborSignal(tile.blockPos)
                    return when (mode) {
                        ModeType.ON -> power > 0
                        ModeType.OFF -> power == 0
                        ModeType.EQUAL -> power == value
                        ModeType.GREATER_THAN -> power > value
                        ModeType.GREATER_EQUAL -> power >= value
                        ModeType.LESS_THAN -> power < value
                        ModeType.LESS_EQUAL -> power <= value
                        ModeType.NOT_EQUAL -> power != value
                    }
                }
            }
        }

        abstract class RTM {
            class SimpleDetectTrain : This() {
                enum class DetectMode(@JvmField val key: String) {
                    All("atsassistmod.IFTTT.DetectMode.0"),
                    FirstCar("atsassistmod.IFTTT.DetectMode.1"),
                    LastCar("atsassistmod.IFTTT.DetectMode.2"),
                    OnRail("atsassistmod.IFTTT.DetectMode.3"),
                }

                var detectMode = DetectMode.All

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.RTM.OnTrain

                override fun getExplanation(): Array<String> = arrayOf("DetectMode: " + detectMode.name)

                override fun setFromGui(gui: IftttEditView) = Unit

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean =
                    when (detectMode) {
                        DetectMode.All,
                        DetectMode.OnRail,
                        -> train != null
                        DetectMode.FirstCar -> train != null &&
                            (RtmTrains.formationSize(train) == 1 ||
                                RtmTrains.connected(train, train.trainDirection.toInt()) == null)
                        DetectMode.LastCar -> train != null &&
                            (RtmTrains.formationSize(train) == 1 ||
                                RtmTrains.connected(train, 1 - train.trainDirection.toInt()) == null)
                    }
            }

            class Cars : This() {
                var value = 0
                var mode: ComparisonManager.Integer = ComparisonManager.Integer.GREATER_EQUAL

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.RTM.Cars

                override fun getExplanation(): Array<String> = arrayOf("Cars" + mode.name + value)

                override fun setFromGui(gui: IftttEditView) {
                    value = gui.getTextFieldInt(0)
                }

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean =
                    train != null && mode.isTrue(RtmTrains.formationSize(train), value)
            }

            class Speed : This() {
                var value = 0
                var mode: ComparisonManager.Integer = ComparisonManager.Integer.GREATER_EQUAL

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.RTM.Speed

                override fun getExplanation(): Array<String> = arrayOf("Speed" + mode.name + value)

                override fun setFromGui(gui: IftttEditView) {
                    value = gui.getTextFieldInt(0)
                }

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean =
                    train != null && mode.isTrue(RtmTrains.speedKmh(train).roundToInt(), value)
            }

            @Suppress("UNCHECKED_CAST")
            class TrainDataMap : This() {
                var dataType = DataType.BOOLEAN
                    set(value) {
                        field = value
                        comparisonType = when (value) {
                            DataType.HEX,
                            DataType.INT,
                            -> ComparisonManager.Integer.EQUAL as ComparisonManager.ComparisonBase<Any?>
                            DataType.DOUBLE -> ComparisonManager.Double.EQUAL as ComparisonManager.ComparisonBase<Any?>
                            DataType.STRING,
                            DataType.VEC,
                            -> ComparisonManager.String.EQUAL as ComparisonManager.ComparisonBase<Any?>
                            DataType.BOOLEAN -> ComparisonManager.Boolean.TRUE as ComparisonManager.ComparisonBase<Any?>
                        }
                    }
                var key: String = ""
                var value: Any? = ""
                var comparisonType: ComparisonManager.ComparisonBase<Any?> = ComparisonManager.Boolean.TRUE as ComparisonManager.ComparisonBase<Any?>

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.RTM.TrainDataMap

                override fun getTitle(): String = getType().getTranslationKey() + " " + dataType.key

                override fun getExplanation(): Array<String> =
                    arrayOf("Key: $key", "Value" + comparisonType.getName() + if (dataType == DataType.BOOLEAN) "" else value)

                override fun setFromGui(gui: IftttEditView) {
                    key = gui.getTextFieldText(0)
                    setValue(gui.getTextFieldText(1))
                }

                @Suppress("UNCHECKED_CAST")
                fun setValue(value: String) {
                    this.value = comparisonType.parseT(value)
                }

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean {
                    if (train == null) return false
                    val dataMap = train.resourceState.dataMap
                    val dataValue: Any? = when (dataType) {
                        DataType.HEX,
                        DataType.INT,
                        -> dataMap.getInt(key)
                        DataType.DOUBLE -> dataMap.getDouble(key)
                        DataType.STRING,
                        DataType.VEC,
                        -> dataMap.getString(key)
                        DataType.BOOLEAN -> dataMap.getBoolean(key)
                    }
                    return try {
                        comparisonType.isTrue(dataValue, value)
                    } catch (_: Exception) {
                        false
                    }
                }
            }

            class TrainDirection : This() {
                var direction = CardinalDirection.NORTH

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.RTM.TrainDirection

                override fun getExplanation(): Array<String> = arrayOf("Train heading " + direction.name)

                override fun setFromGui(gui: IftttEditView) = Unit

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean =
                    train != null && direction.isInDirection(train)
            }
        }

        abstract class ATSAssist {
            class CrossingObstacleDetection : This() {
                var startCC: IntArray = intArrayOf(0, 0, 0)
                var endCC: IntArray = intArrayOf(0, 0, 0)

                fun setStartCC(x: Int, y: Int, z: Int) {
                    startCC = intArrayOf(x, y, z)
                }

                fun setEndCC(x: Int, y: Int, z: Int) {
                    endCC = intArrayOf(x, y, z)
                }

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.This.ATSAssist.CODD

                override fun getExplanation(): Array<String> =
                    arrayOf(
                        String.format("x:%s, y:%s, z:%s", startCC[0], startCC[1], startCC[2]),
                        String.format("x:%s, y:%s, z:%s", endCC[0], endCC[1], endCC[2]),
                    )

                override fun setFromGui(gui: IftttEditView) {
                    setStartCC(gui.getTextFieldInt(0), gui.getTextFieldInt(1), gui.getTextFieldInt(2))
                    setEndCC(gui.getTextFieldInt(3), gui.getTextFieldInt(4), gui.getTextFieldInt(5))
                }

                override fun isCondition(tile: IftttBlockEntity, train: TrainEntity?): Boolean {
                    val box = AABB(
                        min(startCC[0], endCC[0]).toDouble(),
                        min(startCC[1], endCC[1]).toDouble(),
                        min(startCC[2], endCC[2]).toDouble(),
                        max(startCC[0], endCC[0]) + 1.0,
                        max(startCC[1], endCC[1]) + 1.0,
                        max(startCC[2], endCC[2]) + 1.0,
                    )
                    return tile.level!!.getEntitiesOfClass(Entity::class.java, box).any(::isObstacle)
                }
            }
        }
    }

    abstract class That : IFTTTContainer() {
        abstract fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean)

        open fun finish(tile: IftttBlockEntity, train: TrainEntity?) = Unit

        abstract class Minecraft {
            class RedStoneOutput : That() {
                var isTrainCarsOutput = false
                var outputLevel = 0

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.Minecraft.RedStoneOutput

                override fun getExplanation(): Array<String> =
                    arrayOf("Output: " + if (isTrainCarsOutput) "cars" else outputLevel)

                override fun setFromGui(gui: IftttEditView) {
                    outputLevel = gui.getTextFieldInt(0)
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    tile.setRedStoneOutput(if (isTrainCarsOutput) train?.let(RtmTrains::formationSize) ?: 0 else outputLevel)
                }
            }

            class PlaySound : That() {
                var soundName: String? = null
                var pos: IntArray? = null
                var radius = 1

                fun setPos(x: Int, y: Int, z: Int) {
                    pos = intArrayOf(x, y, z)
                }

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.Minecraft.PlaySound

                override fun getExplanation(): Array<String> = arrayOf(soundName ?: "")

                override fun setFromGui(gui: IftttEditView) {
                    soundName = gui.getTextFieldText(0)
                    radius = gui.getTextFieldInt(1)
                    setPos(gui.getTextFieldInt(2), gui.getTextFieldInt(3), gui.getTextFieldInt(4))
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    val soundName = soundName
                    if (first && soundName != null && soundName.matches(Regex(".*:.+"))) {
                        val server = tile.level!!.server ?: return
                        val p = pos ?: intArrayOf(tile.blockPos.x, tile.blockPos.y, tile.blockPos.z)
                        ATSAModNet.broadcastSound(
                            server,
                            SoundPayloads.PlaySoundsAt(arrayListOf(p), arrayListOf(soundName), radius / 16f),
                        )
                    }
                }
            }

            class ExecuteCommand : That() {
                var command: String = ""
                private var displayNameValue: String? = ""

                fun getDisplayName(): String = displayNameValue ?: ""

                fun setDisplayName(displayName: String?) {
                    displayNameValue = displayName
                }

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.Minecraft.ExecuteCommand

                override fun getExplanation(): Array<String> =
                    arrayOf(if (getDisplayName().isEmpty()) "Cmd: $command" else getDisplayName())

                override fun setFromGui(gui: IftttEditView) {
                    setDisplayName(gui.getTextFieldText(0))
                    command = gui.getTextFieldText(1)
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    if (isOnce() && !first) return
                    val server = tile.level!!.server ?: return
                    val pos = tile.blockPos
                    val source: CommandSourceStack = server.createCommandSourceStack()
                        .withPermission(PermissionSet.ALL_PERMISSIONS)
                        .withSuppressedOutput()
                        .withPosition(Vec3.atCenterOf(pos))
                        .withLevel(tile.level as ServerLevel)
                    server.commands.performPrefixedCommand(source, command)
                }
            }

            class SetBlock : That() {
                private val posList = ArrayList<IntArray>()
                var blockId = "minecraft:air"

                fun getPosList(): List<IntArray> = posList

                fun clearPosList() {
                    posList.clear()
                }

                fun addPos(pos: IntArray) {
                    posList.add(pos)
                }

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.Minecraft.SetBlock

                override fun getExplanation(): Array<String> = arrayOf("SetBlock: $blockId")

                override fun setFromGui(gui: IftttEditView) {
                    clearPosList()
                    blockId = gui.getTextFieldText(0)
                    val length = gui.textFieldLength()
                    var i = 1
                    while (i + 2 < length) {
                        addPos(intArrayOf(gui.getTextFieldInt(i), gui.getTextFieldInt(i + 1), gui.getTextFieldInt(i + 2)))
                        i += 3
                    }
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    if (isOnce() && !first) return
                    val level = tile.level!!
                    val identifier = Identifier.tryParse(blockId) ?: return
                    val block: Block = BuiltInRegistries.BLOCK.get(identifier)
                        .map { it.value() }
                        .orElse(null) ?: return
                    for (pos in posList) {
                        level.setBlock(BlockPos(pos[0], pos[1], pos[2]), block.defaultBlockState(), 3)
                    }
                }
            }
        }

        abstract class RTM {
            class DataMap : That() {
                var dataType = DataType.STRING
                var key = ""
                var value = ""

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.RTM.TrainDataMap

                override fun getTitle(): String = getType().getTranslationKey() + " " + dataType.key

                override fun getExplanation(): Array<String> = arrayOf("Key: $key", "Value: $value")

                override fun setFromGui(gui: IftttEditView) {
                    key = gui.getTextFieldText(0)
                    value = gui.getTextFieldText(1)
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    if (train == null) return
                    val dataMap = train.resourceState.dataMap
                    try {
                        when (dataType) {
                            DataType.BOOLEAN -> dataMap.setBoolean(key, value.toBoolean(), 1)
                            DataType.DOUBLE -> dataMap.setDouble(key, value.toDouble(), 1)
                            DataType.INT,
                            DataType.HEX,
                            -> dataMap.setInt(key, value.toInt(), 1)
                            DataType.STRING,
                            DataType.VEC,
                            -> dataMap.setString(key, value, 1)
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            class TrainSignal : That() {
                var signal = 0

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.RTM.Signal

                override fun getExplanation(): Array<String> = arrayOf("SetSignal:$signal")

                override fun setFromGui(gui: IftttEditView) {
                    signal = gui.getTextFieldInt(0)
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) {
                    train?.setSignal2(signal)
                }
            }
        }

        abstract class ATSAssist {
            class JavaScript : That() {
                var jsText: String? = null
                private var error = false
                private var scriptNameValue: String? = ""

                fun setJsTextDirect(jsText: String?) {
                    this.jsText = jsText
                }

                fun setJSText(jsText: String?) {
                    this.jsText = jsText
                    error = false
                }

                fun getJSText(): String? = jsText

                fun getScriptName(): String = scriptNameValue ?: ""

                fun setScriptName(scriptName: String?) {
                    scriptNameValue = scriptName
                }

                override fun getType(): IFTTTType.IFTTTEnumBase = IFTTTType.That.ATSAssist.JavaScript

                override fun getExplanation(): Array<String> = arrayOf(getScriptName() + " (JS unsupported in this port)")

                override fun setFromGui(gui: IftttEditView) {
                    setScriptName(gui.getTextFieldText(0))
                    setJSText(gui.getTextFieldText(1))
                }

                override fun doThat(tile: IftttBlockEntity, train: TrainEntity?, first: Boolean) = Unit
            }
        }
    }

    companion object {
        private const val serialVersionUID = -2781244534093360974L

        @JvmStatic
        private fun isObstacle(entity: Entity): Boolean {
            if (entity is TrainEntity || entity is ItemEntity) return false
            if (entity is TrainBogieEntity) return false
            if (entity is TrainSeatEntity) return false
            return entity.vehicle !is TrainEntity
        }
    }
}

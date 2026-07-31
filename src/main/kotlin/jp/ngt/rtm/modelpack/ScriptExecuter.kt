// SPDX-License-Identifier: LGPL-3.0-or-later
package jp.ngt.rtm.modelpack

import cc.mirukuneko.realtrainmodrenewed.RealTrainModRenewed
import jp.ngt.mccompat.WorldCompat
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import javax.script.Invocable
import javax.script.ScriptEngine

@Suppress("unused")
class ScriptExecuter() {
    @JvmField var count: Long = 0L
    private var level: ServerLevel? = null
    private var pos: Vec3 = Vec3.ZERO
    private var name: String = "RTM Script Executer"

    constructor(level: ServerLevel, pos: Vec3, name: String) : this() {
        this.level = level
        this.pos = pos
        this.name = name
    }

    fun beginScript(caller: Any?) {
        when (caller) {
            is net.minecraft.world.entity.Entity -> {
                level = caller.level() as? ServerLevel
                pos = caller.position()
            }
            is net.minecraft.world.level.block.entity.BlockEntity -> {
                level = caller.level as? ServerLevel
                pos = Vec3.atCenterOf(caller.blockPos)
            }
        }
    }

    fun completeScript() {
        count++
    }

    fun execCommand(command: String?) {
        val actualLevel = level ?: return
        if (command.isNullOrBlank()) return
        try {
            val server = actualLevel.server
            val source = CommandSourceStack(
                CommandSource.NULL, pos, Vec2.ZERO, actualLevel, LevelBasedPermissionSet.GAMEMASTER, name,
                Component.literal(name), server, null,
            )
            server.commands.performPrefixedCommand(source, command)
        } catch (error: Throwable) {
            RealTrainModRenewed.LOGGER.warn("[serverScript] Command failed: {}", command, error)
        }
    }

    fun func_70005_c_(): String = name
    fun getName(): String = name
    fun getCommandSenderName(): String = name
    fun getCount(): Long = count
    fun func_145748_c_(): Component = Component.literal(name)
    fun addChatMessage(message: Any?) = Unit
    fun canCommandSenderUseCommand(permissionLevel: Int, commandName: String?): Boolean = permissionLevel <= 2
    fun func_130014_f_(): WorldCompat? = level?.let(::WorldCompat)
    fun getEntityWorld(): WorldCompat? = func_130014_f_()
    fun getPlayerCoordinates(): IntArray =
        intArrayOf(kotlin.math.floor(pos.x).toInt() shr 4, kotlin.math.floor(pos.y).toInt(), kotlin.math.floor(pos.z).toInt() shr 4)

    fun callMethod(selector: Any?, methodName: String?, vararg args: Any?): Any? {
        if (selector == null || methodName == null) return null
        return try {
            val engine = selector.javaClass.methods
                .firstOrNull { it.name == "getServerScriptEngine" && it.parameterCount == 0 }
                ?.invoke(selector) as? ScriptEngine
            (engine as? Invocable)?.invokeFunction(methodName, *args)
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    fun execScript(selector: Any?) {
        beginScript(selector)
        callMethod(selector, "onUpdate", selector, this)
        completeScript()
    }
}

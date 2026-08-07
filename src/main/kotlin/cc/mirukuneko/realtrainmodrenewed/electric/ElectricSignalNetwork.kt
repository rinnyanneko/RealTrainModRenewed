// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed.electric

import cc.mirukuneko.realtrainmodrenewed.blockentity.SignalConverterBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import java.util.ArrayDeque
import java.util.WeakHashMap

/**
 * Runtime topology for installed wire objects. Wire geometry remains owned by the installed-object
 * renderer; this class only carries RTM integer signal levels between their saved endpoints.
 */
object ElectricSignalNetwork {
    private data class WireEdge(val start: BlockPos, val end: BlockPos)
    private class LevelNetwork {
        val adjacent = HashMap<BlockPos, MutableSet<BlockPos>>()
        val wires = HashMap<BlockPos, WireEdge>()
        val wiresByEndpoint = HashMap<BlockPos, MutableSet<BlockPos>>()
        val wireless = HashMap<Int, MutableSet<BlockPos>>()
        val wirelessValues = HashMap<Int, Int>()
    }

    private val networks = WeakHashMap<Level, LevelNetwork>()
    private val broadcastingChannels = ThreadLocal.withInitial { HashSet<String>() }

    @JvmStatic
    fun registerWire(level: Level?, wirePos: BlockPos, start: BlockPos?, end: BlockPos?) {
        if (level == null || level.isClientSide || start == null || end == null || start == end) return
        val a = start.immutable()
        val b = end.immutable()
        val owner = wirePos.immutable()
        synchronized(this) {
            val network = networks.getOrPut(level) { LevelNetwork() }
            unregisterWireLocked(network, wirePos)
            network.wires[owner] = WireEdge(a, b)
            network.adjacent.getOrPut(a) { HashSet() }.add(b)
            network.adjacent.getOrPut(b) { HashSet() }.add(a)
            network.wiresByEndpoint.getOrPut(a) { HashSet() }.add(owner)
            network.wiresByEndpoint.getOrPut(b) { HashSet() }.add(owner)
        }
        // Re-evaluate the whole joined component: the active source can be behind an insulator,
        // rather than at either endpoint of the newly placed edge.
        recomputeComponents(level, a, b)
    }

    @JvmStatic
    fun unregisterWire(level: Level?, wirePos: BlockPos) {
        if (level == null || level.isClientSide) return
        val edge = synchronized(this) {
            networks[level]?.let { unregisterWireLocked(it, wirePos) }
        } ?: return
        recomputeComponents(level, edge.start, edge.end)
    }

    private fun unregisterWireLocked(network: LevelNetwork, wirePos: BlockPos): WireEdge? {
        val owner = wirePos.immutable()
        val edge = network.wires.remove(owner) ?: return null
        val parallelEdgeRemains = network.wires.values.any {
            (it.start == edge.start && it.end == edge.end) || (it.start == edge.end && it.end == edge.start)
        }
        if (!parallelEdgeRemains) {
            network.adjacent[edge.start]?.let { it.remove(edge.end); if (it.isEmpty()) network.adjacent.remove(edge.start) }
            network.adjacent[edge.end]?.let { it.remove(edge.start); if (it.isEmpty()) network.adjacent.remove(edge.end) }
        }
        network.wiresByEndpoint[edge.start]?.let { it.remove(owner); if (it.isEmpty()) network.wiresByEndpoint.remove(edge.start) }
        network.wiresByEndpoint[edge.end]?.let { it.remove(owner); if (it.isEmpty()) network.wiresByEndpoint.remove(edge.end) }
        return edge
    }

    @JvmStatic
    fun removeWiresAtEndpoint(level: Level?, endpoint: BlockPos) {
        if (level == null || level.isClientSide) return
        val owners = synchronized(this) {
            networks[level]?.wiresByEndpoint?.get(endpoint)?.toList().orEmpty()
        }
        for (owner in owners) level.removeBlock(owner, false)
    }

    @JvmStatic
    fun propagate(level: Level?, origin: BlockPos, signalLevel: Int) {
        if (level == null || level.isClientSide) return
        val adjacency = synchronized(this) {
            networks[level]?.adjacent?.mapValues { (_, value) -> value.toSet() }
        } ?: return
        val queue = ArrayDeque<Pair<BlockPos, Int>>()
        val visited = HashSet<BlockPos>()
        val start = origin.immutable()
        queue.add(start to signalLevel)
        visited.add(start)
        var guard = 0
        while (queue.isNotEmpty() && guard++ < 4096) {
            val (pos, input) = queue.removeFirst()
            val node = level.getBlockEntity(pos) as? ElectricSignalNode
            val output = if (pos == start || node == null) input else node.receiveElectricity(input)
            for (next in adjacency[pos].orEmpty()) {
                if (visited.add(next)) queue.add(next to output)
            }
        }
    }

    @JvmStatic
    fun registerWireless(level: Level?, channel: Int, pos: BlockPos) {
        if (level == null || level.isClientSide) return
        val currentValue = synchronized(this) {
            val network = networks.getOrPut(level) { LevelNetwork() }
            unregisterWirelessLocked(network, pos)
            network.wireless.getOrPut(channel) { HashSet() }.add(pos.immutable())
            network.wirelessValues[channel]
        }
        if (currentValue != null) {
            val receiver = level.getBlockEntity(pos) as? SignalConverterBlockEntity
            receiver?.receiveWireless(currentValue)
            propagate(level, pos, currentValue)
        } else {
            val firstLoadedNode = level.getBlockEntity(pos) as? SignalConverterBlockEntity ?: return
            val restoredValue = firstLoadedNode.getElectricity()
            broadcastWireless(level, channel, pos, restoredValue)
            propagate(level, pos, restoredValue)
        }
    }

    @JvmStatic
    @Synchronized
    fun unregisterWireless(level: Level?, pos: BlockPos) {
        if (level == null || level.isClientSide) return
        networks[level]?.let { unregisterWirelessLocked(it, pos) }
    }

    private fun unregisterWirelessLocked(network: LevelNetwork, pos: BlockPos) {
        val immutable = pos.immutable()
        val iterator = network.wireless.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            entry.value.remove(immutable)
            if (entry.value.isEmpty()) {
                network.wirelessValues.remove(entry.key)
                iterator.remove()
            }
        }
    }

    @JvmStatic
    fun broadcastWireless(level: Level?, channel: Int, sender: BlockPos, signalLevel: Int) {
        if (level == null || level.isClientSide) return
        val key = "${System.identityHashCode(level)}#$channel"
        val active = broadcastingChannels.get()
        if (!active.add(key)) return
        try {
            val receivers = synchronized(this) {
                val network = networks.getOrPut(level) { LevelNetwork() }
                network.wirelessValues[channel] = signalLevel
                network.wireless[channel]?.toList().orEmpty()
            }
            for (receiverPos in receivers) {
                if (receiverPos == sender) continue
                val receiver = level.getBlockEntity(receiverPos) as? SignalConverterBlockEntity ?: continue
                receiver.receiveWireless(signalLevel)
                propagate(level, receiverPos, signalLevel)
            }
        } finally {
            active.remove(key)
        }
    }

    private fun recomputeComponents(level: Level, first: BlockPos, second: BlockPos) {
        val adjacency = synchronized(this) {
            networks[level]?.adjacent?.mapValues { (_, value) -> value.toSet() }.orEmpty()
        }
        val handled = HashSet<BlockPos>()
        for (root in arrayOf(first, second)) {
            if (!handled.add(root)) continue
            val component = ArrayList<BlockPos>()
            val queue = ArrayDeque<BlockPos>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val pos = queue.removeFirst()
                component.add(pos)
                for (next in adjacency[pos].orEmpty()) if (handled.add(next)) queue.add(next)
            }
            val sources = ArrayList<SignalConverterBlockEntity>()
            for (pos in component) {
                val converter = level.getBlockEntity(pos) as? SignalConverterBlockEntity ?: continue
                when (converter.converterType) {
                    SignalConverterType.RS_INPUT, SignalConverterType.WIRELESS -> sources.add(converter)
                    else -> converter.clearWiredSignal()
                }
            }
            for (source in sources) propagate(level, source.blockPos, source.getElectricity())
        }
    }
}

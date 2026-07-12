// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package jp.kaiz.atsassistmod.client.screen

import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity
import jp.kaiz.atsassistmod.client.ClientNetworkHelper
import jp.kaiz.atsassistmod.ifttt.IFTTTContainer
import jp.kaiz.atsassistmod.ifttt.IFTTTType
import jp.kaiz.atsassistmod.ifttt.IFTTTUtil
import jp.kaiz.atsassistmod.ifttt.IftttFactory
import jp.kaiz.atsassistmod.network.payload.IftttPayloads
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Checkbox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import java.util.function.Consumer

/** IFTTT block editor. */
open class IftttEditorScreen(tile: IftttBlockEntity) : Screen(Component.literal("IFTTT")) {
    private val pos: BlockPos = tile.blockPos
    private val thisList = ArrayList<IFTTTContainer>()
    private val thatList = ArrayList<IFTTTContainer>()
    private var anyMatch = tile.isAnyMatch()
    private var addingThis: Boolean? = null

    init {
        for (container in tile.getThisList()) {
            roundTrip(container)?.let(thisList::add)
        }
        for (container in tile.getThatList()) {
            roundTrip(container)?.let(thatList::add)
        }
    }

    override fun init() {
        val adding = addingThis
        if (adding != null) {
            initPicker(adding)
            return
        }
        val colL = width / 2 - 160
        val colR = width / 2 + 10
        val top = 50

        for (i in thisList.indices) {
            val index = i
            val container = thisList[i]
            addRenderableWidget(Button.builder(Component.literal(label(container))) {
                editEntry(container, index, true)
            }.bounds(colL, top + i * 22, 130, 20).build())
            addRenderableWidget(Button.builder(Component.literal("X")) {
                thisList.removeAt(index)
                rebuild()
            }.bounds(colL + 132, top + i * 22, 18, 20).build())
        }
        if (thisList.size < 6) {
            addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.0")) {
                addingThis = true
                rebuild()
            }.bounds(colL, top + thisList.size * 22, 150, 20).build())
        }

        for (i in thatList.indices) {
            val index = i
            val container = thatList[i]
            addRenderableWidget(Button.builder(Component.literal(label(container))) {
                editEntry(container, index, false)
            }.bounds(colR, top + i * 22, 130, 20).build())
            addRenderableWidget(Button.builder(Component.literal("X")) {
                thatList.removeAt(index)
                rebuild()
            }.bounds(colR + 132, top + i * 22, 18, 20).build())
        }
        if (thatList.size < 6) {
            addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.91.0")) {
                addingThis = false
                rebuild()
            }.bounds(colR, top + thatList.size * 22, 150, 20).build())
        }

        addRenderableWidget(
            Checkbox.builder(Component.literal("Any match (OR)"), font)
                .pos(width / 2 - 75, height - 78)
                .selected(anyMatch)
                .onValueChange { _, value -> anyMatch = value }
                .build(),
        )
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.21")) {
            save()
        }.bounds(width / 2 - 100, height - 52, 95, 20).build())
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.GroundUnitMenu.common.button.20")) {
            onClose()
        }.bounds(width / 2 + 5, height - 52, 95, 20).build())
    }

    private fun initPicker(isThis: Boolean) {
        val types = if (isThis) IftttFactory.THIS_TYPES else IftttFactory.THAT_TYPES
        val top = 40
        for (i in types.indices) {
            val typeId = types[i]
            val type = IFTTTType.getType(typeId)
            val label = if (type == null) typeId.toString() else Component.translatable(type.getTranslationKey()).string
            addRenderableWidget(Button.builder(Component.literal(label)) {
                pickType(typeId, isThis)
            }.bounds(width / 2 - 100, top + i * 22, 200, 20).build())
        }
        addRenderableWidget(Button.builder(Component.translatable("atsassistmod.gui.IFTTTMaterial.common.button.990")) {
            addingThis = null
            rebuild()
        }.bounds(width / 2 - 100, height - 30, 200, 20).build())
    }

    private fun pickType(typeId: Int, isThis: Boolean) {
        val created = IftttFactory.create(typeId)
        addingThis = null
        if (created == null) {
            rebuild()
            return
        }
        minecraft!!.setScreen(
            IftttMaterialScreen(
                this,
                created,
                Consumer { container ->
                    if (isThis) {
                        if (thisList.size < 6) thisList.add(container)
                    } else {
                        if (thatList.size < 6) thatList.add(container)
                    }
                },
            ),
        )
    }

    private fun editEntry(container: IFTTTContainer, index: Int, isThis: Boolean) {
        minecraft!!.setScreen(
            IftttMaterialScreen(
                this,
                container,
                Consumer { edited ->
                    if (isThis) thisList[index] = edited else thatList[index] = edited
                },
            ),
        )
    }

    private fun rebuild() {
        clearWidgets()
        init()
    }

    private fun save() {
        val thisData = ArrayList<ByteArray>()
        for (container in thisList) {
            IFTTTUtil.toBytes(container)?.let(thisData::add)
        }
        val thatData = ArrayList<ByteArray>()
        for (container in thatList) {
            IFTTTUtil.toBytes(container)?.let(thatData::add)
        }
        ClientNetworkHelper.sendToServer(IftttPayloads.SaveIfttt(pos, anyMatch, thisData, thatData))
        onClose()
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, partial: Float) {
        extractBackground(g, mouseX, mouseY, partial)
        super.extractRenderState(g, mouseX, mouseY, partial)
        if (addingThis == null) {
            g.text(font, "IF (THIS)", width / 2 - 160, 38, 0xFFFFFF)
            g.text(font, "THEN (THAT)", width / 2 + 10, 38, 0xFFFFFF)
        } else {
            g.centeredText(font, "Select type", width / 2, 24, 0xFFFFFF)
        }
    }

    override fun isPauseScreen(): Boolean = false

    companion object {
        private fun roundTrip(container: IFTTTContainer): IFTTTContainer? {
            val bytes = IFTTTUtil.toBytes(container)
            return if (bytes == null) null else IFTTTUtil.fromBytes(bytes)
        }

        private fun label(container: IFTTTContainer): String {
            val explanation = container.getExplanation()
            val head = Component.translatable(container.getTitle()).string
            return if (explanation.isNotEmpty() && explanation[0].isNotEmpty()) {
                "$head (${explanation[0]})"
            } else {
                head
            }
        }
    }
}

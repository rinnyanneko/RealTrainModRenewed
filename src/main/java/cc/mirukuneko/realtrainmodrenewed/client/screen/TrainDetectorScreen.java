package cc.mirukuneko.realtrainmodrenewed.client.screen;

import cc.mirukuneko.realtrainmodrenewed.blockentity.TrainDetectorBlockEntity;
import cc.mirukuneko.realtrainmodrenewed.client.ClientNetworkHelper;
import cc.mirukuneko.realtrainmodrenewed.network.ConfigureTrainDetectorPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class TrainDetectorScreen extends Screen {
    private final BlockPos pos;
    private EditBox channelBox;
    private EditBox rangeBox;
    private int linkedChannel = -1;
    private int detectionRange = 3;
    private boolean occupied;

    public TrainDetectorScreen(BlockPos pos) {
        super(Component.literal("電車検知ブロック"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        readState();
        int boxWidth = 150;
        int x = (width - boxWidth) / 2;
        int y = height / 2 - 36;
        channelBox = new EditBox(font, x, y, boxWidth, 20, Component.literal("信号番号"));
        channelBox.setMaxLength(10);
        channelBox.setValue(linkedChannel > 0 ? Integer.toString(linkedChannel) : "");
        addRenderableWidget(channelBox);

        rangeBox = new EditBox(font, x, y + 30, boxWidth, 20, Component.literal("検知範囲"));
        rangeBox.setMaxLength(3);
        rangeBox.setValue(Integer.toString(detectionRange));
        addRenderableWidget(rangeBox);
        setInitialFocus(channelBox);

        addRenderableWidget(Button.builder(Component.literal("保存"), button -> submit())
            .bounds(width / 2 - 80, y + 66, 75, 20)
            .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
            .bounds(width / 2 + 5, y + 66, 75, 20)
            .build());
    }

    private void readState() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (minecraft.level.getBlockEntity(pos) instanceof TrainDetectorBlockEntity blockEntity) {
            linkedChannel = blockEntity.getLinkedChannel();
            detectionRange = blockEntity.getDetectionRange();
            occupied = blockEntity.isOccupied();
        }
    }

    private void submit() {
        try {
            int channel = channelBox.getValue().trim().isEmpty() ? -1 : Integer.parseInt(channelBox.getValue().trim());
            int range = Integer.parseInt(rangeBox.getValue().trim());
            ClientNetworkHelper.sendToServer(new ConfigureTrainDetectorPayload(pos, channel, range));
            onClose();
        } catch (NumberFormatException ignored) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendOverlayMessage(Component.literal("数字で入力してください"));
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerY = height / 2;
        graphics.centeredText(font, title, width / 2, centerY - 62, 0xFFFFFF);
        graphics.centeredText(font, Component.literal("占有中: " + (occupied ? "はい" : "いいえ")), width / 2, centerY - 50, occupied ? 0xFF6666 : 0x66FF66);
        graphics.centeredText(font, Component.literal("信号番号を入れると occupied=停止 / clear=進行"), width / 2, centerY - 18, 0xAAAAAA);
        graphics.centeredText(font, Component.literal("空欄なら赤石検知だけ使えます"), width / 2, centerY - 6, 0xAAAAAA);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

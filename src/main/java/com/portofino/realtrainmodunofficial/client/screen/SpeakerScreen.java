package com.portofino.realtrainmodunofficial.client.screen;

import com.portofino.realtrainmodunofficial.blockentity.InstalledObjectBlockEntity;
import com.portofino.realtrainmodunofficial.installedobject.SpeakerSoundConfig;
import com.portofino.realtrainmodunofficial.network.ConfigureSpeakerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * スピーカー設定GUI。
 * <ul>
 *   <li>可聴範囲(ブロック)を設定。</li>
 *   <li>レッドストーン信号強度(1〜15)ごとに鳴らす音(サウンドイベントID)を割り当て。</li>
 *   <li>音名は検索ボックスで候補を絞り込み、クリックで入力欄へ（同時に試聴）。</li>
 * </ul>
 * 本家 RTM の「信号強度で音IDを選ぶ」方式を踏襲（MC のレッドストーンは 0-15。
 * レバー/レッドストーンブロックは強度15なので、割り当ては必ず入力する信号強度に合わせること）。
 */
public class SpeakerScreen extends Screen {
    private static final int MAX_CANDIDATES = 6;
    private static final int BOX_W = 220;
    private static final int ROW_H = 16;

    private final BlockPos pos;
    private EditBox rangeBox;
    private EditBox slotBox;
    private EditBox soundBox;
    private EditBox searchBox;
    private int speakerRange = 32;
    private int listTop;
    private int leftX;
    private final List<Button> candidateButtons = new ArrayList<>();

    public SpeakerScreen(BlockPos pos) {
        super(Component.literal("スピーカー設定"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        readState();
        leftX = (width - BOX_W) / 2;
        int x = leftX;
        // 画面上端から絶対配置にして、GUIスケールが大きくても見切れないようにする。
        int y = 22;

        rangeBox = new EditBox(font, x, y, 120, 18, Component.literal("可聴範囲(ブロック)"));
        rangeBox.setMaxLength(4);
        rangeBox.setValue(Integer.toString(speakerRange));
        addRenderableWidget(rangeBox);
        addRenderableWidget(Button.builder(Component.literal("範囲を設定"), b -> submitRange())
            .bounds(x + 124, y, BOX_W - 124, 18).build());

        int y2 = y + 28;
        slotBox = new EditBox(font, x, y2, 50, 18, Component.literal("信号強度(1-15)"));
        slotBox.setMaxLength(2);
        slotBox.setValue("15");
        addRenderableWidget(slotBox);

        soundBox = new EditBox(font, x + 56, y2, BOX_W - 56, 18, Component.literal("サウンドID"));
        soundBox.setMaxLength(128);
        addRenderableWidget(soundBox);

        addRenderableWidget(Button.builder(Component.literal("この信号レベルに割当"), b -> submitSound())
            .bounds(x, y2 + 20, BOX_W, 18).build());

        int y3 = y2 + 44;
        searchBox = new EditBox(font, x, y3, BOX_W, 18, Component.literal("音を検索"));
        searchBox.setMaxLength(64);
        searchBox.setResponder(s -> rebuildCandidates());
        addRenderableWidget(searchBox);

        listTop = y3 + 22;

        // Done は画面下端に固定 → 必ず押せる。
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
            .bounds(width / 2 - 50, height - 26, 100, 20).build());

        rebuildCandidates();
    }

    private void readState() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getBlockEntity(pos) instanceof InstalledObjectBlockEntity be) {
            speakerRange = be.getSpeakerRange();
        }
    }

    /** 検索語にマッチするサウンドイベントID候補を最大 MAX_CANDIDATES 個ボタン表示する。 */
    private void rebuildCandidates() {
        for (Button b : candidateButtons) {
            removeWidget(b);
        }
        candidateButtons.clear();

        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        String query = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);

        int shown = 0;
        for (ResourceLocation id : mc.getSoundManager().getAvailableSounds()) {
            if (shown >= MAX_CANDIDATES) {
                break;
            }
            String idStr = id.toString();
            if (!query.isEmpty() && !idStr.toLowerCase(Locale.ROOT).contains(query)) {
                continue;
            }
            final String chosen = idStr;
            Button b = Button.builder(Component.literal(idStr), btn -> {
                    soundBox.setValue(chosen);
                    preview(chosen); // クリックで試聴
                })
                .bounds(leftX, listTop + shown * ROW_H, BOX_W, ROW_H - 1)
                .build();
            addRenderableWidget(b);
            candidateButtons.add(b);
            shown++;
        }
    }

    /** その場でクライアント試聴（プレイヤー位置・非減衰）。 */
    private void preview(String soundIdStr) {
        ResourceLocation id = ResourceLocation.tryParse(soundIdStr.trim().toLowerCase(Locale.ROOT));
        if (id == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id), 1.0F, 1.0F));
        }
    }

    private void submitRange() {
        try {
            int range = Integer.parseInt(rangeBox.getValue().trim());
            PacketDistributor.sendToServer(new ConfigureSpeakerPayload(pos, 0, "", Math.max(1, range)));
            speakerRange = Math.max(1, range);
            toast("範囲を " + speakerRange + " に設定しました");
        } catch (NumberFormatException ignored) {
            notifyNumber();
        }
    }

    private void submitSound() {
        try {
            int slot = Integer.parseInt(slotBox.getValue().trim());
            String sound = soundBox.getValue().trim();
            if (slot < 1 || slot > 15) {
                toast("信号強度は 1〜15 で入力してください");
                return;
            }
            PacketDistributor.sendToServer(new ConfigureSpeakerPayload(pos, slot, sound, 0));
            toast(sound.isEmpty()
                ? ("信号強度 " + slot + " の割り当てを解除しました")
                : ("信号強度 " + slot + " → " + sound + " を割り当てました"));
            if (!sound.isEmpty()) {
                preview(sound);
            }
        } catch (NumberFormatException ignored) {
            notifyNumber();
        }
    }

    private void notifyNumber() {
        toast("数字で入力してください");
    }

    private void toast(String msg) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(msg), true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);

        // 現在の信号強度→音の割当を、候補リストの下に1行ずつコンパクト表示。
        int py = listTop + MAX_CANDIDATES * ROW_H + 4;
        graphics.drawString(font, Component.literal("現在の割当 (信号強度=音)"), leftX, py, 0xAAAAAA, false);
        py += 11;
        StringBuilder line = new StringBuilder();
        int perLine = 0;
        for (int i = 1; i <= 15; i++) {
            String s = SpeakerSoundConfig.getSound(i);
            if (s == null) {
                continue;
            }
            String shortS = s.length() > 26 ? s.substring(0, 25) + "…" : s;
            line.append(i).append("=").append(shortS).append("   ");
            if (++perLine >= 1) {
                graphics.drawString(font, Component.literal(line.toString()), leftX, py, 0x88FF88, false);
                py += 10;
                line.setLength(0);
                perLine = 0;
                if (py > height - 32) {
                    break;
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

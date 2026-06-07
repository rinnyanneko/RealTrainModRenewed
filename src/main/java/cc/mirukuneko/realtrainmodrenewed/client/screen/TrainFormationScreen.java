package cc.mirukuneko.realtrainmodrenewed.client.screen;

import cc.mirukuneko.realtrainmodrenewed.client.ClientItemHelper;
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormation;
import cc.mirukuneko.realtrainmodrenewed.formation.TrainFormationData;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleDefinition;
import cc.mirukuneko.realtrainmodrenewed.vehicle.VehicleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class TrainFormationScreen extends Screen {
    private static final int LIST_TOP = 60;
    private static final int LIST_BOTTOM_MARGIN = 80;
    private static final int ITEM_HEIGHT = 30;
    
    private final ItemStack stack;
    private TrainFormation formation;
    private FormationList formationList;
    private Button addButton;
    private Button removeButton;
    private Button saveButton;
    private Button cancelButton;

    public TrainFormationScreen(ItemStack stack) {
        super(Component.translatable("screen.realtrainmodrenewed.train_formation.title"));
        this.stack = stack;
        this.formation = TrainFormationData.getFormation(stack);
        if (formation == null) {
            this.formation = new TrainFormation();
        } else {
            this.formation = formation.copy();
        }
    }

    @Override
    protected void init() {
        formationList = new FormationList(minecraft, width, height - LIST_BOTTOM_MARGIN, LIST_TOP, ITEM_HEIGHT);
        addWidget(formationList);
        
        // Add button
        addButton = Button.builder(Component.literal("+"), btn -> onAddVehicle())
            .bounds(width - 100, height - LIST_BOTTOM_MARGIN + 8, 40, 20)
            .build();
        addRenderableWidget(addButton);
        
        // Remove button
        removeButton = Button.builder(Component.literal("-"), btn -> onRemoveVehicle())
            .bounds(width - 55, height - LIST_BOTTOM_MARGIN + 8, 40, 20)
            .build();
        addRenderableWidget(removeButton);
        
        // Save button
        saveButton = Button.builder(Component.translatable("gui.done"), btn -> onSave())
            .bounds(width / 2 - 155, height - LIST_BOTTOM_MARGIN + 35, 150, 20)
            .build();
        addRenderableWidget(saveButton);
        
        // Cancel button
        cancelButton = Button.builder(Component.translatable("gui.cancel"), btn -> onClose())
            .bounds(width / 2 + 5, height - LIST_BOTTOM_MARGIN + 35, 150, 20)
            .build();
        addRenderableWidget(cancelButton);
        
        updateButtons();
    }

    private void onAddVehicle() {
        // Open vehicle selection screen
        ClientItemHelper.openTrainSelectScreen(this);
    }

    private void onRemoveVehicle() {
        FormationList.FormationEntry selected = formationList.getSelected();
        if (selected != null) {
            formation.removeVehicle(selected.getIndex());
            formationList.refresh();
            updateButtons();
        }
    }

    private void onSave() {
        // Ensure formation has a name for saving
        if (formation.getName().isEmpty()) {
            formation.setName("編成" + System.currentTimeMillis());
        }
        TrainFormationData.setFormation(stack, formation);
        onClose();
    }
    
    // Method to update formation when vehicle is selected
    public void updateFormation(TrainFormation newFormation) {
        this.formation = newFormation;
        formationList.refresh();
        updateButtons();
    }
    
    // Method to add vehicle to formation
    public void updateFormationWithVehicle(String vehicleId) {
        formation.addVehicle(vehicleId);
        formationList.refresh();
        updateButtons();
    }

    private void updateButtons() {
        addButton.active = !formation.isFull();
        removeButton.active = formationList.getSelected() != null && !formation.isEmpty();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        
        graphics.centeredText(font, getTitle(), width / 2, 10, 0xFFFFFF);
        graphics.text(font, Component.translatable("screen.realtrainmodrenewed.train_formation.name"), width / 2 - 150, 35, 0xFFFFFF);
        
        if (formation.isEmpty()) {
            graphics.centeredText(font,
                Component.translatable("screen.realtrainmodrenewed.train_formation.empty"),
                width / 2, height / 2, 0xAAAAAA);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class FormationList extends ObjectSelectionList<FormationList.FormationEntry> {
        FormationList(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
            refresh();
        }

        public void refresh() {
            clearEntries();
            for (int i = 0; i < formation.getCarCount(); i++) {
                String vehicleId = formation.getVehicle(i);
                VehicleDefinition def = VehicleRegistry.getById(vehicleId);
                String displayName = def != null ? def.getDisplayName() : vehicleId;
                addEntry(new FormationEntry(i, displayName));
            }
        }

        @Override
        protected int scrollBarX() {
            return width - 8;
        }

        @Override
        public int getRowWidth() {
            return width - 20;
        }

        class FormationEntry extends ObjectSelectionList.Entry<FormationEntry> {
            private final int index;
            private final Component label;

            FormationEntry(int index, String displayName) {
                this.index = index;
                this.label = Component.literal((index + 1) + "F: " + displayName);
            }

            public int getIndex() {
                return index;
            }

            @Override
            public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int left = getX();
                int top = getY();
                int width = getWidth();
                int height = getHeight();
                int color = hovered ? 0xFFFF55 : 0xFFFFFF;
                if (FormationList.this.getSelected() == this) {
                    graphics.fill(left, top, left + width, top + height, 0x44FFFFFF);
                }
                graphics.text(font, label, left + 6, top + (height - 8) / 2, color);
            }

            @Override
            public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
                if (event.button() == 0) {
                    FormationList.this.setSelected(this);
                    updateButtons();
                    return true;
                }
                return false;
            }

            @Override
            public Component getNarration() {
                return label;
            }
        }
    }
}

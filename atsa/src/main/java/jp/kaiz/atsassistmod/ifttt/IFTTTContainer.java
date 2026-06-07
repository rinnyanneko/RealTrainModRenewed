package jp.kaiz.atsassistmod.ifttt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainBogieEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainSeatEntity;
import cc.mirukuneko.realtrainmodrenewed.entity.TrainEntity;
import jp.kaiz.atsassistmod.block.entity.IftttBlockEntity;
import jp.kaiz.atsassistmod.network.ATSAModNet;
import jp.kaiz.atsassistmod.network.payload.SoundPayloads;
import jp.kaiz.atsassistmod.rtm.RtmTrains;
import jp.kaiz.atsassistmod.util.CardinalDirection;
import jp.kaiz.atsassistmod.util.ComparisonManager;
import jp.kaiz.atsassistmod.util.DataType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * IFTTT rule container (faithful port). Jackson polymorphic by class name, so this
 * mirrors the original class structure. RTM-specific gaps in the new API are
 * approximated and documented inline (signal set / JS / on-rail detect / numeric
 * block ids). Conditions evaluate against the optional {@link TrainEntity} on the block.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class IFTTTContainer implements Serializable {
    private static final long serialVersionUID = -2781244534093360974L;
    protected boolean once;

    public abstract IFTTTType.IFTTTEnumBase getType();

    public String getTitle() {
        return getType() == null ? "" : getType().getTranslationKey();
    }

    public abstract String[] getExplanation();

    public abstract void setFromGui(IftttEditView gui);

    public void setOnce(boolean once) { this.once = once; }
    public boolean isOnce() { return once; }

    // ============================================================ This
    public abstract static class This extends IFTTTContainer {
        private static final long serialVersionUID = 4458077452851594500L;
        public abstract boolean isCondition(IftttBlockEntity tile, TrainEntity train);

        public abstract static class Minecraft {
            public static class RedStoneInput extends This {
                private static final long serialVersionUID = 2180620082205172167L;
                public enum ModeType {
                    ON("ON", false), OFF("OFF", false), EQUAL("==", true), GREATER_THAN(">", true),
                    GREATER_EQUAL(">=", true), LESS_THAN("<", true), LESS_EQUAL("<=", true), NOT_EQUAL("!=", true);
                    public final String name;
                    public final boolean needStr;
                    ModeType(String name, boolean needStr) { this.name = name; this.needStr = needStr; }
                }
                private int value;
                private ModeType mode = ModeType.ON;

                public ModeType getMode() { return mode; }
                public int getValue() { return value; }
                public void setMode(ModeType mode) { this.mode = mode; }
                public void setValue(int value) { this.value = value; }

                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.Minecraft.RedStoneInput; }
                public String[] getExplanation() { return new String[]{"RSInput" + mode.name + (mode.needStr ? value : "")}; }
                public void setFromGui(IftttEditView gui) { setValue(gui.getTextFieldInt(0)); }

                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    int power = tile.getLevel().getBestNeighborSignal(tile.getBlockPos());
                    return switch (mode) {
                        case ON -> power > 0;
                        case OFF -> power == 0;
                        case EQUAL -> power == value;
                        case GREATER_THAN -> power > value;
                        case GREATER_EQUAL -> power >= value;
                        case LESS_THAN -> power < value;
                        case LESS_EQUAL -> power <= value;
                        case NOT_EQUAL -> power != value;
                    };
                }
            }
        }

        public abstract static class RTM {
            public static class SimpleDetectTrain extends This {
                private static final long serialVersionUID = -6173509528806558810L;
                public enum DetectMode {
                    All("atsassistmod.IFTTT.DetectMode.0"), FirstCar("atsassistmod.IFTTT.DetectMode.1"),
                    LastCar("atsassistmod.IFTTT.DetectMode.2"), OnRail("atsassistmod.IFTTT.DetectMode.3");
                    public final String key;
                    DetectMode(String key) { this.key = key; }
                }
                private DetectMode detectMode = DetectMode.All;
                public DetectMode getDetectMode() { return detectMode; }
                public void setDetectMode(DetectMode detectMode) { this.detectMode = detectMode; }

                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.RTM.OnTrain; }
                public String[] getExplanation() { return new String[]{"DetectMode: " + detectMode.name()}; }
                public void setFromGui(IftttEditView gui) { }

                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    return switch (detectMode) {
                        case All, OnRail -> train != null; // OnRail approximated as "train present" (no rail-graph API)
                        case FirstCar -> train != null && (RtmTrains.formationSize(train) == 1
                                || RtmTrains.connected(train, (int) train.getTrainDirection()) == null);
                        case LastCar -> train != null && (RtmTrains.formationSize(train) == 1
                                || RtmTrains.connected(train, 1 - (int) train.getTrainDirection()) == null);
                    };
                }
            }

            public static class Cars extends This {
                private static final long serialVersionUID = -2131617513036808484L;
                private int value;
                private ComparisonManager.Integer comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                public ComparisonManager.Integer getMode() { return comparisonType; }
                public int getValue() { return value; }
                public void setMode(ComparisonManager.Integer mode) { this.comparisonType = mode; }
                public void setValue(int value) { this.value = value; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.RTM.Cars; }
                public String[] getExplanation() { return new String[]{"Cars" + comparisonType.getName() + value}; }
                public void setFromGui(IftttEditView gui) { setValue(gui.getTextFieldInt(0)); }
                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    return train != null && comparisonType.isTrue(RtmTrains.formationSize(train), value);
                }
            }

            public static class Speed extends This {
                private static final long serialVersionUID = 6976046959593179672L;
                private int value;
                private ComparisonManager.Integer comparisonType = ComparisonManager.Integer.GREATER_EQUAL;
                public ComparisonManager.Integer getMode() { return comparisonType; }
                public int getValue() { return value; }
                public void setMode(ComparisonManager.Integer mode) { this.comparisonType = mode; }
                public void setValue(int value) { this.value = value; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.RTM.Speed; }
                public String[] getExplanation() { return new String[]{"Speed" + comparisonType.getName() + value}; }
                public void setFromGui(IftttEditView gui) { setValue(gui.getTextFieldInt(0)); }
                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    return train != null && comparisonType.isTrue(Math.round(RtmTrains.speedKmh(train)), value);
                }
            }

            public static class TrainDataMap extends This {
                private static final long serialVersionUID = -8546481139822877274L;
                private DataType dataType = DataType.BOOLEAN;
                private String key = "";
                private Object value = "";
                private ComparisonManager.ComparisonBase comparisonType = ComparisonManager.Boolean.TRUE;

                public DataType getDataType() { return dataType; }
                public void setDataType(DataType dataType) {
                    this.dataType = dataType;
                    this.comparisonType = switch (dataType) {
                        case HEX, INT -> ComparisonManager.Integer.EQUAL;
                        case DOUBLE -> ComparisonManager.Double.EQUAL;
                        case STRING, VEC -> ComparisonManager.String.EQUAL;
                        case BOOLEAN -> ComparisonManager.Boolean.TRUE;
                    };
                }
                public ComparisonManager.ComparisonBase getComparisonType() { return comparisonType; }
                public String getKey() { return key; }
                public void setKey(String key) { this.key = key; }
                public Object getValue() { return value; }
                @SuppressWarnings("unchecked")
                public void setValue(String value) { this.value = comparisonType.parseT(value); }

                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.RTM.TrainDataMap; }
                public String getTitle() { return getType().getTranslationKey() + " " + dataType.key; }
                public String[] getExplanation() {
                    return new String[]{"Key: " + key, "Value" + comparisonType.getName() + (dataType == DataType.BOOLEAN ? "" : value)};
                }
                public void setFromGui(IftttEditView gui) { setKey(gui.getTextFieldText(0)); setValue(gui.getTextFieldText(1)); }

                @SuppressWarnings("unchecked")
                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    if (train == null) return false;
                    var dataMap = train.getResourceState().getDataMap();
                    Object dv = switch (dataType) {
                        case HEX, INT -> dataMap.getInt(key);
                        case DOUBLE -> dataMap.getDouble(key);
                        case STRING, VEC -> dataMap.getString(key);
                        case BOOLEAN -> dataMap.getBoolean(key);
                    };
                    try {
                        return comparisonType.isTrue(dv, value);
                    } catch (Exception e) {
                        return false;
                    }
                }
            }

            public static class TrainDirection extends This {
                private static final long serialVersionUID = -2660351192067336593L;
                private CardinalDirection direction = CardinalDirection.NORTH;
                public CardinalDirection getDirection() { return direction; }
                public void setDirection(CardinalDirection direction) { this.direction = direction; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.RTM.TrainDirection; }
                public String[] getExplanation() { return new String[]{"Train heading " + direction.name()}; }
                public void setFromGui(IftttEditView gui) { }
                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    return train != null && direction.isInDirection(train);
                }
            }
        }

        public abstract static class ATSAssist {
            public static class CrossingObstacleDetection extends This {
                private static final long serialVersionUID = -2345201548431087396L;
                private int[] startCC = {0, 0, 0};
                private int[] endCC = {0, 0, 0};
                public void setStartCC(int x, int y, int z) { startCC = new int[]{x, y, z}; }
                public int[] getStartCC() { return startCC; }
                public void setEndCC(int x, int y, int z) { endCC = new int[]{x, y, z}; }
                public int[] getEndCC() { return endCC; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.This.ATSAssist.CODD; }
                public String[] getExplanation() {
                    return new String[]{
                            String.format("x:%s, y:%s, z:%s", startCC[0], startCC[1], startCC[2]),
                            String.format("x:%s, y:%s, z:%s", endCC[0], endCC[1], endCC[2])};
                }
                public void setFromGui(IftttEditView gui) {
                    setStartCC(gui.getTextFieldInt(0), gui.getTextFieldInt(1), gui.getTextFieldInt(2));
                    setEndCC(gui.getTextFieldInt(3), gui.getTextFieldInt(4), gui.getTextFieldInt(5));
                }
                public boolean isCondition(IftttBlockEntity tile, TrainEntity train) {
                    AABB box = new AABB(
                            Math.min(startCC[0], endCC[0]), Math.min(startCC[1], endCC[1]), Math.min(startCC[2], endCC[2]),
                            Math.max(startCC[0], endCC[0]) + 1, Math.max(startCC[1], endCC[1]) + 1, Math.max(startCC[2], endCC[2]) + 1);
                    return tile.getLevel().getEntitiesOfClass(Entity.class, box).stream()
                            .anyMatch(IFTTTContainer::isObstacle);
                }
            }
        }
    }

    // ============================================================ That
    public abstract static class That extends IFTTTContainer {
        private static final long serialVersionUID = 3885084343670120809L;
        public abstract void doThat(IftttBlockEntity tile, TrainEntity train, boolean first);
        public void finish(IftttBlockEntity tile, TrainEntity train) { }

        public abstract static class Minecraft {
            public static class RedStoneOutput extends That {
                private static final long serialVersionUID = -4456412974039197107L;
                private boolean trainCarsOutput;
                private int outputLevel;
                public void setTrainCarsOutput(boolean v) { this.trainCarsOutput = v; }
                public boolean isTrainCarsOutput() { return trainCarsOutput; }
                public void setOutputLevel(int v) { this.outputLevel = v; }
                public int getOutputLevel() { return outputLevel; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.Minecraft.RedStoneOutput; }
                public String[] getExplanation() { return new String[]{"Output: " + (trainCarsOutput ? "cars" : outputLevel)}; }
                public void setFromGui(IftttEditView gui) { setOutputLevel(gui.getTextFieldInt(0)); }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    tile.setRedStoneOutput(trainCarsOutput ? (train != null ? RtmTrains.formationSize(train) : 0) : outputLevel);
                }
            }

            public static class PlaySound extends That {
                private static final long serialVersionUID = -6941622798294195533L;
                private String soundName;
                private int[] pos;
                private int radius = 1;
                public PlaySound() { }
                public void setSoundName(String s) { this.soundName = s; }
                public String getSoundName() { return soundName; }
                public void setPos(int x, int y, int z) { this.pos = new int[]{x, y, z}; }
                public int[] getPos() { return pos; }
                public void setRadius(int r) { this.radius = r; }
                public int getRadius() { return radius; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.Minecraft.PlaySound; }
                public String[] getExplanation() { return new String[]{soundName == null ? "" : soundName}; }
                public void setFromGui(IftttEditView gui) {
                    setSoundName(gui.getTextFieldText(0));
                    setRadius(gui.getTextFieldInt(1));
                    setPos(gui.getTextFieldInt(2), gui.getTextFieldInt(3), gui.getTextFieldInt(4));
                }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    // Repeat playback is not supported by the new sequence player; plays once on trigger.
                    if (first && soundName != null && soundName.matches(".*:.+")) {
                        MinecraftServer server = tile.getLevel().getServer();
                        if (server == null) return;
                        int[] p = pos != null ? pos : new int[]{tile.getBlockPos().getX(), tile.getBlockPos().getY(), tile.getBlockPos().getZ()};
                        List<int[]> positions = new ArrayList<>();
                        positions.add(p);
                        List<String> orders = new ArrayList<>();
                        orders.add(soundName);
                        ATSAModNet.broadcastSound(server, new SoundPayloads.PlaySoundsAt(positions, orders, radius / 16f));
                    }
                }
            }

            public static class ExecuteCommand extends That {
                private static final long serialVersionUID = -83401892282647225L;
                private String command = "";
                private String displayName = "";
                public void setCommand(String c) { this.command = c; }
                public String getCommand() { return command; }
                public void setDisplayName(String d) { this.displayName = d; }
                public String getDisplayName() { return displayName == null ? "" : displayName; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.Minecraft.ExecuteCommand; }
                public String[] getExplanation() { return new String[]{getDisplayName().isEmpty() ? ("Cmd: " + command) : displayName}; }
                public void setFromGui(IftttEditView gui) { setDisplayName(gui.getTextFieldText(0)); setCommand(gui.getTextFieldText(1)); }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    if (this.once && !first) return;
                    MinecraftServer server = tile.getLevel().getServer();
                    if (server == null) return;
                    BlockPos pos = tile.getBlockPos();
                    CommandSourceStack source = server.createCommandSourceStack()
                            .withPermission(PermissionSet.ALL_PERMISSIONS)
                            .withSuppressedOutput()
                            .withPosition(Vec3.atCenterOf(pos))
                            .withLevel((net.minecraft.server.level.ServerLevel) tile.getLevel());
                    server.getCommands().performPrefixedCommand(source, command);
                }
            }

            public static class SetBlock extends That {
                private static final long serialVersionUID = -696087836237577609L;
                // 1.21 has no numeric block ids; positions carry a registry name instead of id/meta.
                private final List<int[]> posList = new ArrayList<>();
                private String blockId = "minecraft:air";
                public SetBlock() { }
                public List<int[]> getPosList() { return posList; }
                public void clearPosList() { posList.clear(); }
                public void addPos(int[] pos) { posList.add(pos); }
                public String getBlockId() { return blockId; }
                public void setBlockId(String blockId) { this.blockId = blockId; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.Minecraft.SetBlock; }
                public String[] getExplanation() { return new String[]{"SetBlock: " + blockId}; }
                public void setFromGui(IftttEditView gui) {
                    clearPosList();
                    setBlockId(gui.getTextFieldText(0));
                    int len = gui.textFieldLength();
                    for (int i = 1; i + 2 < len; i += 3) {
                        addPos(new int[]{gui.getTextFieldInt(i), gui.getTextFieldInt(i + 1), gui.getTextFieldInt(i + 2)});
                    }
                }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    if (this.once && !first) return;
                    Level level = tile.getLevel();
                    Block block = BuiltInRegistries.BLOCK.get(Identifier.tryParse(blockId)).map(net.minecraft.core.Holder::value).orElse(null);
                    if (block == null) return;
                    posList.forEach(p -> level.setBlock(new BlockPos(p[0], p[1], p[2]), block.defaultBlockState(), 3));
                }
            }
        }

        public abstract static class RTM {
            public static class DataMap extends That {
                private static final long serialVersionUID = -5927011065086566182L;
                private DataType dataType = DataType.STRING;
                private String key = "";
                private String value = "";
                public DataType getDataType() { return dataType; }
                public void setDataType(DataType dataType) { this.dataType = dataType; }
                public String getKey() { return key; }
                public void setKey(String key) { this.key = key; }
                public String getValue() { return value; }
                public void setValue(String value) { this.value = value; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.RTM.TrainDataMap; }
                public String getTitle() { return getType().getTranslationKey() + " " + dataType.key; }
                public String[] getExplanation() { return new String[]{"Key: " + key, "Value: " + value}; }
                public void setFromGui(IftttEditView gui) { setKey(gui.getTextFieldText(0)); setValue(gui.getTextFieldText(1)); }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    if (train == null) return;
                    var dataMap = train.getResourceState().getDataMap();
                    try {
                        switch (dataType) {
                            case BOOLEAN -> dataMap.setBoolean(key, Boolean.parseBoolean(value), 1);
                            case DOUBLE -> dataMap.setDouble(key, Double.parseDouble(value), 1);
                            case INT, HEX -> dataMap.setInt(key, Integer.parseInt(value), 1);
                            case STRING, VEC -> dataMap.setString(key, value, 1);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            public static class TrainSignal extends That {
                private static final long serialVersionUID = 7174373529070856419L;
                private int signal;
                public int getSignal() { return signal; }
                public void setSignal(int signal) { this.signal = signal; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.RTM.Signal; }
                public String[] getExplanation() { return new String[]{"SetSignal:" + signal}; }
                public void setFromGui(IftttEditView gui) { setSignal(gui.getTextFieldInt(0)); }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    // RTM's new API has no train signal setter (getSignal is a stub); no-op.
                }
            }
        }

        public abstract static class ATSAssist {
            public static class JavaScript extends That {
                private static final long serialVersionUID = 1661419614469936838L;
                private String jsText;
                private boolean error;
                private String scriptName = "";
                public String getJSText() { return jsText; }
                public void setJsTextDirect(String jsText) { this.jsText = jsText; }
                public void setJSText(String jsText) { this.jsText = jsText; this.error = false; }
                public String getScriptName() { return scriptName == null ? "" : scriptName; }
                public void setScriptName(String s) { this.scriptName = s; }
                public IFTTTType.IFTTTEnumBase getType() { return IFTTTType.That.ATSAssist.JavaScript; }
                public String[] getExplanation() { return new String[]{getScriptName() + " (JS unsupported in this port)"}; }
                public void setFromGui(IftttEditView gui) { setScriptName(gui.getTextFieldText(0)); setJSText(gui.getTextFieldText(1)); }
                public void doThat(IftttBlockEntity tile, TrainEntity train, boolean first) {
                    // RTM's NGT ScriptUtil is unavailable; JavaScript actions are a no-op in this port.
                }
            }
        }
    }

    private static boolean isObstacle(Entity e) {
        if (e instanceof TrainEntity || e instanceof ItemEntity) return false;
        if (e instanceof TrainBogieEntity) return false;
        if (e instanceof TrainSeatEntity) return false;
        Entity vehicle = e.getVehicle();
        return !(vehicle instanceof TrainEntity);
    }
}

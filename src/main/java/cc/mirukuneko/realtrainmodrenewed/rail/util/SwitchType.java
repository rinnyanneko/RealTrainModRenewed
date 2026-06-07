package cc.mirukuneko.realtrainmodrenewed.rail.util;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public abstract class SwitchType {
    public final byte id;
    protected RailMapSwitch[] railMaps = new RailMapSwitch[0];
    protected Point[] points = new Point[0];

    protected SwitchType(int id) {
        this.id = (byte) id;
    }

    public abstract boolean init(List<RailPosition> switchList, List<RailPosition> normalList);

    public void onBlockChanged(Level level) {
    }

    public void onUpdate(Level level) {
        for (Point point : this.points) {
            if (point != null) {
                point.onUpdate(level);
            }
        }
    }

    public RailMapSwitch[] getAllRailMap() {
        return this.railMaps;
    }

    public Point[] getPoints() {
        return this.points;
    }

    public List<RailMap> getOpenRailMaps() {
        if (this.id == 3) {
            return List.of(this.railMaps);
        }
        List<RailMap> open = new ArrayList<>(this.railMaps.length);
        for (RailMapSwitch railMap : this.railMaps) {
            if (railMap != null && railMap.isOpen()) {
                open.add(railMap);
            }
        }
        return open.isEmpty() ? List.of(this.railMaps) : open;
    }

    public int firstOpenRailIndex() {
        if (this.id == 3) {
            return 0;
        }
        for (int i = 0; i < this.railMaps.length; i++) {
            if (this.railMaps[i] != null && this.railMaps[i].isOpen()) {
                return i;
            }
        }
        return 0;
    }

    public int[] getOpenRailIndices() {
        if (this.id == 3) {
            int[] indices = new int[this.railMaps.length];
            for (int i = 0; i < this.railMaps.length; i++) {
                indices[i] = i;
            }
            return indices;
        }
        int count = 0;
        for (RailMapSwitch railMap : this.railMaps) {
            if (railMap != null && railMap.isOpen()) {
                count++;
            }
        }
        if (count == 0) {
            return new int[]{0};
        }
        int[] indices = new int[count];
        int write = 0;
        for (int i = 0; i < this.railMaps.length; i++) {
            if (this.railMaps[i] != null && this.railMaps[i].isOpen()) {
                indices[write++] = i;
            }
        }
        return indices;
    }

    public static class SwitchBasic extends SwitchType {
        public SwitchBasic() {
            super(0);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            if (switchList.size() != 1 || normalList.size() != 2) {
                return false;
            }
            RailPosition root = switchList.get(0);
            RailPosition branch1 = normalList.get(0);
            RailPosition branch2 = normalList.get(1);
            RailDir dir = root.getDir(branch1, branch2);
            this.railMaps = new RailMapSwitch[]{
                new RailMapSwitch(root, branch1, dir, RailDir.NONE),
                new RailMapSwitch(root, branch2, dir.invert(), RailDir.NONE)
            };
            this.points = new Point[]{
                new Point(root, railMaps[0], railMaps[1]),
                new Point(branch1, railMaps[0]),
                new Point(branch2, railMaps[1])
            };
            return true;
        }

        @Override
        public void onBlockChanged(Level level) {
            if (level == null || this.railMaps.length < 2) {
                return;
            }
            if (this.railMaps[0].getStartRP().checkRSInput(level)) {
                this.railMaps[0].setState(false);
                this.railMaps[1].setState(true);
            } else {
                this.railMaps[0].setState(true);
                this.railMaps[1].setState(false);
            }
        }
    }

    public static class SwitchSingleCross extends SwitchType {
        public SwitchSingleCross() {
            super(1);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            if (switchList.size() != 2 || normalList.size() != 2) {
                return false;
            }
            RailPosition root1 = switchList.get(0);
            RailPosition root2 = switchList.get(1);
            RailDir b0 = RailDir.NONE;
            RailDir b1 = RailDir.NONE;
            RailMapSwitch[] rails = new RailMapSwitch[3];

            RailPosition branch0 = findFirstDifferentDirection(root1, normalList, null);
            RailPosition branch1 = findFirstDifferentDirection(root2, normalList, branch0);
            if (branch0 == null || branch1 == null) {
                return false;
            }

            b0 = root1.getDir(root2, branch0);
            b1 = root2.getDir(root1, branch1);
            rails[0] = new RailMapSwitch(root1, branch0, b0.invert(), RailDir.NONE);
            rails[1] = new RailMapSwitch(root2, branch1, b1.invert(), RailDir.NONE);
            rails[2] = new RailMapSwitch(root1, root2, b0, b1);
            this.railMaps = rails;
            this.points = new Point[]{
                new Point(root1, rails[0], rails[2]),
                new Point(root2, rails[1], rails[2]),
                new Point(rails[0].getStartRP() == root1 ? rails[0].getEndRP() : rails[0].getStartRP(), rails[0]),
                new Point(rails[1].getStartRP() == root2 ? rails[1].getEndRP() : rails[1].getStartRP(), rails[1])
            };
            return true;
        }

        @Override
        public void onBlockChanged(Level level) {
            if (level == null || this.railMaps.length < 3) {
                return;
            }
            if (this.railMaps[2].isGettingPowered(level)) {
                this.railMaps[0].setState(false);
                this.railMaps[1].setState(false);
                this.railMaps[2].setState(true);
            } else {
                this.railMaps[0].setState(true);
                this.railMaps[1].setState(true);
                this.railMaps[2].setState(false);
            }
        }
    }

    public static class SwitchScissorsCross extends SwitchType {
        public SwitchScissorsCross() {
            super(2);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            if (switchList.size() != 4) {
                return false;
            }

            RailMapSwitch[] rails = new RailMapSwitch[4];
            RailPosition[][] pairs = new RailPosition[4][2];
            int pairCount = 0;
            for (int i = 0; i < 4; ++i) {
                for (int j = i + 1; j < 4; ++j) {
                    int dirDiff = Math.abs((switchList.get(i).direction & 7) - (switchList.get(j).direction & 7));
                    if (dirDiff > 4) {
                        dirDiff = 8 - dirDiff;
                    }
                    if (dirDiff > 2 && pairCount < 4) {
                        pairs[pairCount++] = new RailPosition[]{switchList.get(i), switchList.get(j)};
                    }
                }
            }
            if (pairCount != 4) {
                return false;
            }

            this.railMaps = new RailMapSwitch[4];
            for (int i = 0; i < 4; ++i) {
                RailDir dir0 = RailDir.NONE;
                RailDir dir1 = RailDir.NONE;
                for (int j = 0; j < 4; ++j) {
                    if (i == j) {
                        continue;
                    }
                    if (pairs[i][0] == pairs[j][0]) {
                        dir0 = pairs[i][0].getDir(pairs[i][1], pairs[j][1]);
                    } else if (pairs[i][0] == pairs[j][1]) {
                        dir0 = pairs[i][0].getDir(pairs[i][1], pairs[j][0]);
                    } else if (pairs[i][1] == pairs[j][0]) {
                        dir1 = pairs[i][1].getDir(pairs[i][0], pairs[j][1]);
                    } else if (pairs[i][1] == pairs[j][1]) {
                        dir1 = pairs[i][1].getDir(pairs[i][0], pairs[j][0]);
                    }
                }
                rails[i] = new RailMapSwitch(pairs[i][0], pairs[i][1], dir0, dir1);
            }
            this.railMaps = rails;

            this.points = new Point[4];
            for (int i = 0; i < 4; ++i) {
                RailPosition rp = switchList.get(i);
                RailMapSwitch rms1 = null;
                RailMapSwitch rms2 = null;
                for (RailMapSwitch railMap : this.railMaps) {
                    if (railMap.getStartRP() == rp || railMap.getEndRP() == rp) {
                        if (rms1 == null) {
                            rms1 = railMap;
                        } else {
                            rms2 = railMap;
                            break;
                        }
                    }
                }
                this.points[i] = new Point(rp, rms1, rms2);
            }
            return true;
        }

        @Override
        public void onBlockChanged(Level level) {
            if (level == null) {
                return;
            }
            RailMapSwitch openRms = null;
            for (int phase = 0; phase < 2; ++phase) {
                for (RailMapSwitch rms : this.railMaps) {
                    if (rms.startDir == rms.endDir) {
                        if (phase == 0) {
                            if (rms.isGettingPowered(level)) {
                                openRms = rms;
                                break;
                            }
                        } else if (rms == openRms) {
                            rms.setState(true);
                        } else {
                            rms.setState(false);
                        }
                    } else if (phase == 1) {
                        rms.setState(openRms == null);
                    }
                }
            }
        }
    }

    public static class SwitchDiamondCross extends SwitchType {
        public SwitchDiamondCross() {
            super(3);
        }

        @Override
        public boolean init(List<RailPosition> switchList, List<RailPosition> normalList) {
            List<RailPosition> all = new ArrayList<>(switchList.size() + normalList.size());
            all.addAll(switchList);
            all.addAll(normalList);
            if (all.size() != 4) {
                return false;
            }
            RailMapSwitch[] rails = new RailMapSwitch[2];
            int k = 0;
            for (int i = 0; i < 4; ++i) {
                for (int j = i + 1; j < 4; ++j) {
                    if (Math.abs((all.get(i).direction & 7) - (all.get(j).direction & 7)) == 4) {
                        rails[k++] = new RailMapSwitch(all.get(i), all.get(j), RailDir.NONE, RailDir.NONE).setState(true);
                        if (k >= 2) {
                            this.railMaps = rails;
                            this.points = new Point[]{
                                new Point(rails[0].getStartRP(), rails[0]),
                                new Point(rails[0].getEndRP(), rails[0]),
                                new Point(rails[1].getStartRP(), rails[1]),
                                new Point(rails[1].getEndRP(), rails[1])
                            };
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    private static RailPosition findFirstDifferentDirection(RailPosition switchMarker, List<RailPosition> normalMarkers, RailPosition used) {
        RailPosition fallback = null;
        for (RailPosition normal : normalMarkers) {
            if (used != null && sameEndpoint(used, normal)) {
                continue;
            }
            if ((switchMarker.direction & 7) != (normal.direction & 7)) {
                return normal;
            }
            if (fallback == null) {
                fallback = normal;
            }
        }
        return fallback;
    }

    private static boolean sameEndpoint(RailPosition a, RailPosition b) {
        return a != null && b != null && a.blockX == b.blockX && a.blockY == b.blockY && a.blockZ == b.blockZ;
    }

}

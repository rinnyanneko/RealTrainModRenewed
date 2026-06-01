package com.portofino.realtrainmodunofficial.rail.util;

import java.util.ArrayList;
import java.util.List;

public final class RailMaker {
    private final List<RailPosition> rpList;

    public RailMaker(List<RailPosition> positions) {
        this.rpList = new ArrayList<>(positions);
    }

    public RailMaker(RailPosition[] positions) {
        this.rpList = new ArrayList<>(positions.length);
        for (RailPosition position : positions) {
            if (position != null) {
                this.rpList.add(position);
            }
        }
    }

    private SwitchType getSwitchType() {
        if (this.rpList.size() == 3) {
            int count = 0;
            for (RailPosition rp : this.rpList) {
                count += rp.switchType == 1 ? 1 : 0;
            }
            if (count == 1) {
                return new SwitchType.SwitchBasic();
            }
        } else if (this.rpList.size() == 4) {
            int count = 0;
            for (RailPosition rp : this.rpList) {
                count += rp.switchType == 1 ? 1 : 0;
            }
            if (count == 2) {
                return new SwitchType.SwitchSingleCross();
            }
            if (count == 4) {
                for (int i = 0; i < this.rpList.size(); ++i) {
                    for (int j = i + 1; j < this.rpList.size(); ++j) {
                        if ((this.rpList.get(i).direction & 7) == (this.rpList.get(j).direction & 7)) {
                            return new SwitchType.SwitchScissorsCross();
                        }
                    }
                }
                return new SwitchType.SwitchDiamondCross();
            }
        }
        return null;
    }

    public SwitchType getSwitch() {
        SwitchType type = this.getSwitchType();
        if (type == null) {
            return null;
        }

        List<RailPosition> switchList = new ArrayList<>();
        List<RailPosition> normalList = new ArrayList<>();
        for (RailPosition rp : this.rpList) {
            if (rp.switchType == 1) {
                switchList.add(rp);
            } else {
                normalList.add(rp);
            }
        }
        return type.init(switchList, normalList) ? type : null;
    }
}

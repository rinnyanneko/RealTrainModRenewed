package jp.kaiz.atsassistmod.ifttt;

/** IFTTT condition/action type ids (faithful port). Names resolve to lang keys. */
public final class IFTTTType {
    private IFTTTType() {}

    public interface IFTTTEnumBase {
        int getId();
        default String getTranslationKey() {
            return "atsassistmod.IFTTTType." + getId();
        }
    }

    public enum This implements IFTTTEnumBase {
        Select(100);
        private final int id;
        This(int id) { this.id = id; }
        public int getId() { return id; }

        public enum Minecraft implements IFTTTEnumBase {
            RedStoneInput(110);
            private final int id;
            Minecraft(int id) { this.id = id; }
            public int getId() { return id; }
        }

        public enum RTM implements IFTTTEnumBase {
            OnTrain(120), Cars(121), Speed(122), TrainDataMap(124), TrainDirection(125);
            private final int id;
            RTM(int id) { this.id = id; }
            public int getId() { return id; }
        }

        public enum ATSAssist implements IFTTTEnumBase {
            CODD(130);
            private final int id;
            ATSAssist(int id) { this.id = id; }
            public int getId() { return id; }
        }
    }

    public enum That implements IFTTTEnumBase {
        Select(200);
        private final int id;
        That(int id) { this.id = id; }
        public int getId() { return id; }

        public enum Minecraft implements IFTTTEnumBase {
            RedStoneOutput(210), PlaySound(211), ExecuteCommand(212), SetBlock(213);
            private final int id;
            Minecraft(int id) { this.id = id; }
            public int getId() { return id; }
        }

        public enum RTM implements IFTTTEnumBase {
            TrainDataMap(221), Signal(223);
            private final int id;
            RTM(int id) { this.id = id; }
            public int getId() { return id; }
        }

        public enum ATSAssist implements IFTTTEnumBase {
            JavaScript(230);
            private final int id;
            ATSAssist(int id) { this.id = id; }
            public int getId() { return id; }
        }
    }

    public static IFTTTEnumBase getType(int id) {
        if (id >= 100 && id < 106) return This.Select;
        for (IFTTTEnumBase t : This.Minecraft.values()) if (t.getId() == id) return t;
        for (IFTTTEnumBase t : This.RTM.values()) if (t.getId() == id) return t;
        for (IFTTTEnumBase t : This.ATSAssist.values()) if (t.getId() == id) return t;
        if (id >= 200 && id < 206) return That.Select;
        for (IFTTTEnumBase t : That.Minecraft.values()) if (t.getId() == id) return t;
        for (IFTTTEnumBase t : That.RTM.values()) if (t.getId() == id) return t;
        for (IFTTTEnumBase t : That.ATSAssist.values()) if (t.getId() == id) return t;
        return null;
    }
}

package jp.kaiz.atsassistmod.controller.trainprotection;

public enum TrainProtectionType {
    NONE("atsassistmod.trainprotection.none", 0, TrainProtection.class),
    STATION_PREMISES("atsassistmod.trainprotection.station_premises", 1, StationPremisesController.class),
    ATACS("atsassistmod.trainprotection.atacs", 10, ATACSController.class),
    ATSPs("atsassistmod.trainprotection.atsps", 11, ATSPsController.class),
    RATS("atsassistmod.trainprotection.rats", 12, RATSController.class),
    RnATS("atsassistmod.trainprotection.rnats", 13, RnATSController.class);

    public final String translationKey;
    public final int id;
    public final Class<? extends TrainProtection> aClass;

    TrainProtectionType(String translationKey, int id, Class<? extends TrainProtection> aClass) {
        this.translationKey = translationKey;
        this.id = id;
        this.aClass = aClass;
    }

    /** Translation key; UI code wraps with {@code Component.translatable}. */
    public String getTranslationKey() {
        return this.translationKey;
    }

    public TrainProtection newInstance() {
        try {
            return this.aClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to instantiate TrainProtection " + name(), e);
        }
    }

    public static TrainProtectionType getType(int id) {
        for (TrainProtectionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return NONE;
    }
}

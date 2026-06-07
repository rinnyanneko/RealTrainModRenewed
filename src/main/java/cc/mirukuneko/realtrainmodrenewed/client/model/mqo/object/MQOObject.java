package cc.mirukuneko.realtrainmodrenewed.client.model.mqo.object;

import org.jetbrains.annotations.NotNull;

public record MQOObject(
    String name,
    boolean isSmoothShadingEnabled,
    float autoSmoothAngle,
    int mirrorType,
    boolean isMirrorAxisXEnabled,
    boolean isMirrorAxisYEnabled,
    boolean isMirrorAxisZEnabled,
    float mirrorDistance,
    MQOVertex[] vertices,
    MQOFace[] faces
) {
    @Override
    public @NotNull String toString() {
        return "MQOObject{" +
            "name='" + name + '\'' +
            ", isSmoothShadingEnabled=" + isSmoothShadingEnabled +
            ", autoSmoothAngle=" + autoSmoothAngle +
            ", mirrorType=" + mirrorType +
            ", isMirrorAxisXEnabled=" + isMirrorAxisXEnabled +
            ", isMirrorAxisYEnabled=" + isMirrorAxisYEnabled +
            ", isMirrorAxisZEnabled=" + isMirrorAxisZEnabled +
            ", mirrorDistance=" + mirrorDistance +
            ", vertices=" + java.util.Arrays.toString(vertices) +
            ", faces=" + java.util.Arrays.toString(faces) +
            '}';
    }
}
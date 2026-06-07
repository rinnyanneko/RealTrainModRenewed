package cc.mirukuneko.realtrainmodrenewed.item;

public record SelectableModelInfo(String id, String displayName, String packName, String buttonTexture, String category) {
    public SelectableModelInfo(String id, String displayName, String packName, String buttonTexture) {
        this(id, displayName, packName, buttonTexture, packName);
    }
}

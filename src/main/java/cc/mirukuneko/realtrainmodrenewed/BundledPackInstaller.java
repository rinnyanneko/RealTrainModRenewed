package cc.mirukuneko.realtrainmodrenewed;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;

/**
 * Bundled pack を mods フォルダへ同期しつつ、古い不要 pack を掃除する。
 */
public final class BundledPackInstaller {
    private static final Set<String> REMOVED_BUNDLED_PACKS = Set.of(
        "hi03CatenaryPack Common v02.zip",
        "hi03ExpressRailway Catenary w51.zip",
        "hi03ExpressRailway Catenary.zip",
        "hi03ExpressRailway RailAssets.zip",
        "hi03ExpressRailway Rails1067mm.zip"
    );

    private BundledPackInstaller() {
    }

    public static void installDefaultPacks() {
        try {
            installBundledPacksToMods();
            removeDeprecatedPacks();
        } catch (Exception e) {
            RealTrainModRenewed.LOGGER.warn("Could not clean bundled default packs", e);
        }
    }

    private static void installBundledPacksToMods() throws IOException {
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        Files.createDirectories(modsDir);
        for (String category : new String[]{"rail", "installed_object", "vehicle"}) {
            for (Path bundledPack : BundledPackStore.listBundledPacks(category)) {
                Path target = modsDir.resolve(bundledPack.getFileName().toString());
                Files.copy(bundledPack, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
    }

    private static void removeDeprecatedPacks() throws IOException {
        Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
        if (!Files.isDirectory(modsDir)) {
            return;
        }
        for (String fileName : REMOVED_BUNDLED_PACKS) {
            Path target = modsDir.resolve(fileName);
            if (Files.exists(target)) {
                Files.delete(target);
                RealTrainModRenewed.LOGGER.info("Removed deprecated bundled pack from mods folder: {}", fileName);
            }
        }
    }

}

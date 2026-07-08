// SPDX-License-Identifier: LGPL-3.0-or-later
// Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
package cc.mirukuneko.realtrainmodrenewed

import net.neoforged.fml.loading.FMLPaths
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Bundled pack を mods フォルダへ同期しつつ、古い不要 pack を掃除する。
 */
object BundledPackInstaller {
    private val REMOVED_BUNDLED_PACKS = setOf(
        "hi03CatenaryPack Common v02.zip",
        "hi03ExpressRailway Catenary w51.zip",
        "hi03ExpressRailway Catenary.zip",
        "hi03ExpressRailway RailAssets.zip",
        "hi03ExpressRailway Rails1067mm.zip"
    )

    @JvmStatic
    fun installDefaultPacks() {
        try {
            installBundledPacksToMods()
            removeDeprecatedPacks()
        } catch (e: Exception) {
            RealTrainModRenewed.LOGGER.warn("Could not clean bundled default packs", e)
        }
    }

    @Throws(IOException::class)
    private fun installBundledPacksToMods() {
        val modsDir: Path = FMLPaths.GAMEDIR.get().resolve("mods")
        Files.createDirectories(modsDir)
        for (category in arrayOf("rail", "installed_object", "vehicle")) {
            for (bundledPack in BundledPackStore.listBundledPacks(category)) {
                val target = modsDir.resolve(bundledPack.fileName.toString())
                Files.copy(bundledPack, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
            }
        }
    }

    @Throws(IOException::class)
    private fun removeDeprecatedPacks() {
        val modsDir: Path = FMLPaths.GAMEDIR.get().resolve("mods")
        if (!Files.isDirectory(modsDir)) {
            return
        }
        for (fileName in REMOVED_BUNDLED_PACKS) {
            val target = modsDir.resolve(fileName)
            if (Files.exists(target)) {
                Files.delete(target)
                RealTrainModRenewed.LOGGER.info("Removed deprecated bundled pack from mods folder: {}", fileName)
            }
        }
    }
}

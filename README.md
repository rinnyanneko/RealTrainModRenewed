# RealTrainModRenewed

RealTrainModRenewed is a modernization fork of [RealTrainModUnofficial](https://github.com/325-Sunnygo/RealTrainModUnofficial).

The goal of this project is to make RealTrainMod easier to maintain, more developer-friendly, and compatible with modern Minecraft and NeoForge versions, while preserving compatibility with the original RTM ecosystem as much as possible.

## Repositories

- **Main repository:** https://code.mirukuneko.cc/mirukuneko/RealTrainModRenewed
- **Issues and pull requests:** https://codeberg.org/mirukuneko/RealTrainModRenewed
- **GitHub mirror:** https://github.com/rinnyanneko/RealTrainModRenewed

## About RealTrainMod

RealTrainMod, originally created by NGT5479, is a Minecraft mod that adds Japanese trains, tracks, signals, railway-related blocks, and other railway systems to Minecraft.

The original RTM mainly targeted older Minecraft versions such as 1.7.10 and 1.12.2.  
RealTrainModRenewed aims to bring the RTM experience to newer Minecraft versions and keep it maintainable for future development.

## Project Goals

- Port RealTrainMod to modern Minecraft versions.
- Support Minecraft Java Edition 26.1 with NeoForge as the initial modernization target.
- Migrate to the next NeoForge LTS version when it becomes available and is suitable for long-term mod support.
- Keep compatibility with original RTM content where possible.
- Improve code maintainability and developer experience.
- Gradually rewrite suitable parts of the codebase in Kotlin.
- Provide a cleaner foundation for future features.

## Milestones

- [x] Upgrade to Minecraft Java Edition 26.1 / NeoForge 26.1
- [x] Make the mod compile and run on the new target version
- [x] Preserve compatibility with existing RTM models, packs, and content where possible
- [ ] **Gradually rewrite the codebase with Kotlin**
- [ ] FIX BUUUUUUGS
- [ ] Improve documentation for users and developers
- [ ] Evaluate migration to the next NeoForge LTS version when available
- [ ] Add new features where appropriate

Have an idea or found a problem?  
Please open an issue on [Codeberg](https://codeberg.org/mirukuneko/RealTrainModRenewed/issues).

## Development Status

This project is currently in early development.

The current priority is to port the existing Java codebase to Minecraft Java Edition 26.1 / NeoForge 26.1 before large-scale Kotlin refactoring.

Minecraft 26.1 is used as the initial modernization target.  
When the next NeoForge LTS version becomes available, this project may migrate to it as the new long-term target.

Breaking changes may happen during this stage.

## Current Port Notes

The active target is Minecraft Java Edition `26.1.2` with NeoForge `26.1.2.73`
on Java `25`. Keep the Java port stable before starting broad Kotlin work.

Recent compatibility work focuses on preserving legacy RTM add-on behavior:

- Legacy rail, vehicle, installed-object, model, texture, and sound packs can be
  read with common old ZIP entry-name encodings such as MS932 and Shift_JIS.
- The model-selection UI, add-on `buttonTexture` icons, train cab HUD readouts,
  train acceleration/braking, coupling distance, and Graal.js script-engine
  fallback have active 26.1 compatibility fixes.
- Some old RTM/ATSA APIs are still compatibility stubs and need runtime
  verification with representative add-on packs before release.

Safe Kotlin candidates after runtime verification are small utility or pure data
areas such as `UnitConverter`, `PackTextDecoder`, `PackZipReader`, immutable
definition records, and isolated rail math helpers. Avoid converting entry
points, registries, packets, entities, block entities, renderers, and ATSA
integration until the 26.1 runtime path is stable.

## Contributing

Contributions are welcome.

Please use the Codeberg repository for issues and pull requests:

https://codeberg.org/mirukuneko/RealTrainModRenewed

Before making large architectural changes, please open an issue first so the direction can be discussed.

## License and Attribution

This project is licensed under the GNU Lesser General Public License v3.0 or later.

RealTrainModRenewed is a fork of [RealTrainModUnofficial](https://github.com/325-Sunnygo/RealTrainModUnofficial) and is based on the original RealTrainMod project by NGT5479.

Original copyright notices, license files, and attribution are preserved where applicable.

See [LICENSE](./LICENSE) for details.

## Support

If you enjoy this project, please consider giving it a star.

You can also support development here:

https://mirukuneko.cc/donate

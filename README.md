<!--
  SPDX-License-Identifier: LGPL-3.0-or-later
  Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
-->
# RealTrainModRenewed

[![License: LGPL v3](https://img.shields.io/badge/License-LGPL%20v3-blue.svg)](./LICENSE)
[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen)](https://minecraft.net)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1.2.73-orange)](https://neoforged.net)

RealTrainModRenewed is a modernization fork of
[RealTrainModUnofficial](https://github.com/325-Sunnygo/RealTrainModUnofficial)
for Minecraft Java Edition 26.1 with NeoForge, written in Kotlin and Java.

It brings Japanese trains, tracks, signals, and railway systems to modern
Minecraft while maintaining compatibility with the existing RTM ecosystem.

---

## Downloads

- **CurseForge:** https://www.curseforge.com/minecraft/mc-mods/realtrainmodrenewed
- **Modrinth:** https://modrinth.com/mod/realtrainmodrenewed
- **Releases:** https://code.mirukuneko.cc/mirukuneko/RealTrainModRenewed/releases

---

## Repositories

- **Main:** https://code.mirukuneko.cc/mirukuneko/RealTrainModRenewed
- **Issues & PRs:** https://codeberg.org/mirukuneko/RealTrainModRenewed
- **Mirror:** https://github.com/rinnyanneko/RealTrainModRenewed

---

## Goals

- [x] Port RealTrainMod to modern Minecraft with NeoForge
- [ ] **Maintain compatibility with legacy RTM model packs, vehicles, rails, signals**
- [x] Gradually rewrite the codebase in Kotlin
- [ ] **Improve maintainability and developer experience**
- [ ] Target the next NeoForge LTS when available
- [ ] New feature...?

---

## Build

| Component | Version |
|-----------|---------|
| Minecraft | 26.1.2 |
| NeoForge  | 26.1.2.73 |
| Java      | 25 |
| Kotlin    | 2.4.0 |
| Gradle    | 9.1 |

```bash
./gradlew build
```

---

## Known Issues

- Legacy model scripts need compatibility work (JavaScripts and OpenGL APIs)
- Some sound scripts need exact mapping verification

---

## License

This project is licensed under the **GNU Lesser General Public License v3.0
or later**. See [LICENSE](./LICENSE) and [NOTICE.md](./NOTICE.md) for full
terms and attribution.

RealTrainModRenewed is a fork of
[RealTrainModUnofficial](https://github.com/325-Sunnygo/RealTrainModUnofficial)
and is based on the original RealTrainMod by NGT5479. Compatibility behavior is
also checked against ecosystem references such as
[KaizPatchX](https://github.com/Kai-Z-JP/KaizPatchX) and
[AppleExtended](https://github.com/ringo-1234/AppleExtended).

```
SPDX-License-Identifier: LGPL-3.0-or-later
Copyright © 2026 mirukuneko and RealTrainModRenewed contributors
```

---

## Contributing

Please use the Codeberg repository for issues and pull requests:

https://codeberg.org/mirukuneko/RealTrainModRenewed

Before making large architectural changes, open an issue first to discuss the
direction.

---

## Support

⭐ Star the project if you enjoy it — it helps!

You can also support development at https://mirukuneko.cc/donate

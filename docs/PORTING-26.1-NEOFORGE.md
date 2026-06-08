# Minecraft 26.1 / NeoForge 26.1 Port Status

This fork is a modernization of RealTrainModRenewed, not a full redesign. Keep
the Java port working before introducing Kotlin rewrites.

## Target Build

- Minecraft: `26.1.2`
- NeoForge: `26.1.2.73`
- Java toolchain: `25`
- Gradle wrapper: `9.1.0`
- Build plugin: `net.neoforged.moddev` `2.0.141`
- Main mod id: `realtrainmodrenewed`
- Companion ATSA mod id: `atsassistmod`

The previous verified baseline was Minecraft `1.21.1` / NeoForge `21.1.209`.
The 26.1 port is now the active target. `.\gradlew.bat build` is green for the
root RTMU jar and the separate ATSA companion jar. `.\gradlew.bat :runClient`
launches Minecraft `26.1.2`, loads NeoForge `26.1.2.73`, enters a development
world, opens RTMU model-selection UI, and shuts down cleanly in the current
smoke test.

## Completed 26.1 Compile Port

- Gradle, ModDevGradle, Java toolchain, and NeoForge target are updated to the
  26.1 line.
- Main RTMU client/entity/block/entity-renderer code compiles against the 26.1
  render-state extraction and submission APIs.
- Block entities and saved data now use the 26.1 `ValueInput` / `ValueOutput`
  and `SavedDataType` serialization paths where required.
- Item, packet, command, cooldown, registry, tooltip, and resource-loading API
  drift has been patched for the active source tree.
- Legacy client `@OnlyIn` class annotations that caused NeoForge 26.1 runtime
  warnings have been removed from client-only classes.
- Built-in resource paths that Minecraft indexes directly have been normalized
  to lowercase 26.1 resource identifiers, including bundled RTM sound ids and
  ground-unit texture filenames.
- 26.1 item definition bridge files now point at the existing legacy
  `models/item` JSONs so registered items resolve through the current item
  model loader.
- Rail/wrench preview overlay rendering has been restored on the 26.1 level
  render stage using buffered line rendering.
- Screen background extraction has been updated for the 26.1 GUI render-state
  pipeline to avoid the double-blur crash in RTMU configuration/model screens.
- Marker red/blue tinting has been restored through the 26.1 block tint-source
  event and constant item tint definitions.
- Legacy RTM add-on packs with non-UTF-8 ZIP entry names are read through
  `PackZipReader`, which retries common legacy encodings such as MS932 and
  Shift_JIS for rail, vehicle, installed-object, model, texture, and sound-pack
  loading paths.
- Train cab HUD speed and brake needles are visible again through a small
  line-rendered fallback, and the numeric speed/brake readouts use opaque ARGB
  colors under the 26.1 GUI pipeline.
- Train acceleration, braking, coupling gap, and short client interpolation have
  been tuned toward original RTM behavior while keeping the existing Java entity
  code in place.
- Legacy JavaScript engine discovery now falls back to available Graal.js
  `ScriptEngineFactory` instances and avoids disabling scripts on the first
  transient runtime failure.
- Legacy `NGTText.readText(...)` script calls now resolve real pack resources
  instead of returning an empty stub, so add-ons that `eval` shared JS files for
  rollsign, monitor, or sound behavior can load those helpers again.
- Vehicle running sounds prefer a working `soundScript`; JSON running sound
  fields are used only as a compatibility fallback when a sound script is absent
  or cannot be loaded.
- Coupled train spacing uses a tighter RTM-style coupler clearance, and
  formation followers now publish their per-tick movement delta instead of a
  zero vector so Minecraft client interpolation has a continuous motion hint.
- Brake pipe/cylinder/reservoir pressure is now tracked as gradual TrainEntity
  state, and brake deceleration is driven from cylinder pressure instead of
  jumping directly from the current brake notch.
- The fallback cab HUD now uses higher-contrast needles and backed numeric
  readouts, and legacy door fallback matching recognizes common RTM names such
  as `doorFL1`, `doorFR1`, `doorL`, and `doorR`.
- When a legacy sound script loads but does not start a looping train sound, the
  known RTM running-sound fallback can still provide movement audio instead of
  leaving the train silent.
- The generated external sound bridge now sanitizes legacy sound asset paths,
  sounds.json event keys, and `sounds/...ogg` references so 26.1 no longer
  rejects packs with spaces or other invalid identifier characters.
- The sound bridge also repairs missing generated sound references by duplicating
  uniquely matching `.ogg` files into the path expected by legacy sounds.json
  entries, and unknown legacy sound scripts fall back to trailer running audio
  instead of leaving trains silent.
- Cab HUD fallback needles were reduced to thin overlay lines so they remain
  readable without obscuring add-on cab panel artwork, and the client lerp for
  the train currently ridden by the local player is shortened to reduce visible
  cab jitter.
- ATSA still builds as a separate companion jar and has been moved through the
  same 26.1 GUI, block entity, packet, key mapping, renderer, and registry API
  changes.

## Porting Rules

- Keep public behavior, registry ids, packet ids, resource paths, saved data, and
  model-pack formats compatible unless a breaking change is explicitly approved.
- Preserve original license notices and attribution.
- Do not merge `atsa` into the root mod unless the architecture is discussed
  first; it currently builds as a separate companion jar.
- Prefer Java fixes until the 26.1 NeoForge runtime path is stable.
- Use Kotlin later for narrow, low-risk replacements rather than a broad
  source-tree conversion.
- Kotlin source sets are now available in the root project. Keep conversions
  small and preserve Java binary/source interop for existing callers.
- Until the project requires a separate Kotlin loader dependency, the produced
  root jar bundles the Kotlin standard library so the early Kotlin utilities run
  in Prism/NeoForge without asking players to install KotlinForForge separately.

## Known Compatibility Gaps

These are intentionally documented instead of silently removed:

- ATSA JavaScript IFTTT actions are unsupported in this port.
- ATSA data-map editor and train-protection selector GUI wiring still has TODOs.
- ATSA signal setter integration is limited because the current RTMU signal API
  compatibility surface is still stubbed in places.
- Installed-object slanted placement and side-connectivity support is incomplete.
- Some legacy script/runtime APIs are compatibility stubs for old RTM packs.
- MP4 model texture support is disabled unless an optional video decoding library
  is added later.
- MQO model direct immediate/VBO rendering is temporarily disabled. Models use
  the buffered path; this preserves visible rendering intent but may regress the
  old fullbright/VBO optimization until the renderer pipeline rewrite is done.
- The model-selection screen no longer crashes under the 26.1 GUI pipeline and
  attempts the restored preview path, including add-on `buttonTexture` icons,
  but representative external model packs still need manual preview verification.
- The train HUD cab overlay uses line-rendered needle fallbacks instead of the
  original rotating needle sprites. This preserves visible driving information,
  but exact legacy needle artwork and vanilla XP-layer hiding behavior still
  need final visual comparison.
- ATSA ground-unit locator rendering uses 26.1 line rendering instead of the old
  lightning/beacon-style beam render type.
- ATSA ground-unit item variant names no longer override `Item#getDescriptionId`
  because that method is final in 26.1. Variant placement behavior is preserved;
  variant localization needs a new component-based naming approach.
- ATSA IFTTT serialized rule byte payloads are preserved through
  `Codec.BYTE_BUFFER`, but the low-level NBT shape differs from the old
  hand-built compound-list representation.

## Safe Kotlin Candidates After Runtime Verification

Start with code that has limited NeoForge lifecycle coupling and clean Java
interop:

- `cc.mirukuneko.realtrainmodrenewed.util.UnitConverter` is already converted
  to Kotlin and preserves the Java `UnitConverter` static API through
  `@file:JvmName`.
- `cc.mirukuneko.realtrainmodrenewed.util.PackTextDecoder` is already
  converted to Kotlin and preserves the Java `PackTextDecoder` static API
  through `@file:JvmName`.
- `cc.mirukuneko.realtrainmodrenewed.util.PackZipReader`
- Small immutable data holders such as model-pack definitions
- Pure rail math helpers under `rail/math`
- Focused pack-loader parsing helpers after tests or sample-pack checks exist

Avoid early Kotlin conversion for entry points, registry classes, payload
registration, renderers, entities, block entities, and ATSA integration classes.
Those areas are sensitive to lifecycle ordering, sided loading, and Java
compatibility.

## Verification Checklist

- `.\gradlew.bat compileJava` passes.
- `.\gradlew.bat build` passes.
- `.\gradlew.bat :runServer` reaches dedicated-server startup (`Done`) for the
  root RTMU mod and starts the WebCTC compatibility server.
- `.\gradlew.bat runServer` verifies the ATSA companion run can load RTMU and
  reach dedicated-server startup (`Done`). Running root and ATSA server tasks
  together can race for port `25565`; run them separately for smoke tests.
- `.\gradlew.bat :runClient` passes the current client smoke test: initial
  resource reload, integrated development world startup, model-selection screen
  render, integrated server shutdown, and client shutdown all complete.
- Place and break core rail, marker, train, signal, speaker, and crossing-gate
  blocks in a development world.
- Load representative external rail, vehicle, installed-object, and train packs.
- Confirm ATSA loads as a separate jar with RTMU present.
- Record any feature that remains stubbed, disabled, or broken before release.

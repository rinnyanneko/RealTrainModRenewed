# Agent Instructions

## Core Principle

Modernize the implementation.

Preserve the railway experience.

Maintain ecosystem compatibility.

Preserve pack compatibility.

This project prioritizes behavioral compatibility over implementation compatibility.

The objective is not to preserve legacy code.

The objective is to preserve the experience, compatibility, and ecosystem expectations established by RTM, RTMU, KaizPatchX, and ATSAssistMod while modernizing the codebase.

---

## Repository Snapshot

* Project: RealTrainModRenewed
* Minecraft Version: 26.1
* Mod Loader: NeoForge 26.1
* Main Mod ID: `realtrainmodrenewed`
* Main Package: `cc.mirukuneko.realtrainmodrenewed`

This repository is a modernization and continuation of the RTM ecosystem.

The repository contains:

* Java source code
* Kotlin source code
* Model JSON files
* PNG textures
* OGG audio files
* JavaScript scripts
* MQO/MQOZ models
* WebCTC resources
* Pack conversion tools
* Import/export tooling

The repository contains a large amount of legacy behavior that is compatibility-sensitive.

---

## Important Paths

### Root Build Configuration

* `build.gradle`
* `settings.gradle`
* `gradle.properties`

### Main Mod

* `src/main/java/cc/mirukuneko/realtrainmodrenewed/RealTrainModRenewed.java`
* `src/main/java/cc/mirukuneko/realtrainmodrenewed/`

### Resources

* `src/main/resources/META-INF/neoforge.mods.toml`
* `src/main/resources/assets/`

### ATSAssistMod Companion Project

* `atsa/build.gradle`
* `atsa/src/main/java/jp/kaiz/atsassistmod/ATSAssistMod.java`

### Tooling

* `tools/`

---

## Project Goals

### Immediate Goal

Maintain a stable and functional NeoForge 26.1 port.

### Medium-Term Goal

Incrementally migrate suitable components to Kotlin.

### Long-Term Goal

Create a maintainable, modern, performant RTM successor while preserving compatibility with the existing ecosystem.

---

## RTM Ecosystem

This repository is part of a larger RTM ecosystem.

Changes must consider ecosystem-wide compatibility, not only repository-level correctness.

The following projects are ecosystem-critical:

* RealTrainModUnofficial
* KaizPatchX
* ATSAssistMod

Whenever practical, preserve ecosystem compatibility.

---

## Reference Projects

### RealTrainModUnofficial

Repository:

https://github.com/325-Sunnygo/RealTrainModUnofficial

Use as a reference for:

* RTMU behavior
* Compatibility decisions
* Migration approaches
* Historical implementation details

Do not blindly copy code.

Understand why an implementation exists before replacing it.

### KaizPatchX

Repository:

https://github.com/Kai-Z-JP/KaizPatchX

Use as a reference for:

* Feature behavior
* ATS functionality
* WebCTC functionality
* Legacy compatibility fixes
* RTM ecosystem expectations
* Modernization approaches

When implementing functionality previously supported by KaizPatchX, inspect existing implementations before designing new systems.

### ATSAssistMod

Repository:

https://github.com/Kai-Z-JP/ATSAssistMod

Use as a reference for:

* ATS behavior
* ATC behavior
* Railway signaling
* Automation systems
* IFTTT integrations
* Web integrations
* Legacy compatibility behavior

When modifying the `atsa` project, inspect original ATSAssistMod behavior before redesigning systems.

### Reference Priority

When determining expected behavior:

1. Current repository implementation
2. RealTrainModUnofficial
3. KaizPatchX
4. ATSAssistMod
5. Original RTM behavior
6. New implementation

---

## Compatibility Philosophy

Behavioral compatibility is more important than implementation compatibility.

Internal systems may be:

* Refactored
* Rewritten
* Re-architected
* Replaced

when doing so improves:

* Performance
* Stability
* Maintainability
* Testability
* Kotlin interoperability
* NeoForge integration

The existing implementation is not authoritative.

Observable behavior is authoritative.

---

## Railway Simulation Priority

When architecture quality conflicts with railway simulation compatibility:

prefer railway simulation compatibility.

When modernization conflicts with pack compatibility:

prefer pack compatibility.

When code elegance conflicts with ecosystem stability:

prefer ecosystem stability.

---

## User Experience Compatibility

Users familiar with:

* RTM
* RTMU
* KaizPatchX
* ATSAssistMod

should generally experience the same behavior.

Preserve whenever practical:

* Train handling
* Vehicle operation feel
* Brake behavior
* Acceleration behavior
* Signal behavior
* ATS behavior
* ATC behavior
* WebCTC workflows
* Existing player workflows
* Existing operator workflows
* Camera behavior
* Rendering expectations
* Pack-loading workflows

Internal architecture may change significantly as long as the user experience remains consistent.

---

## Pack Compatibility Requirements

Pack compatibility is one of the highest priorities of this project.

Whenever practical, maintain compatibility with:

* RTM packs
* RTMU packs
* KaizPatchX-compatible packs
* ATSAssistMod-compatible content
* Vehicle packs
* Rail packs
* Signal packs
* Scenery packs
* Structure packs
* Model packs

Breaking pack compatibility requires explicit user approval.

When redesigning systems, prefer:

* Compatibility layers
* Adapters
* Migration mechanisms

over requiring pack authors to modify existing content.

Compatibility shims are acceptable.

---

## ATSAssistMod Compatibility

ATSAssistMod is considered a first-class ecosystem component.

Preserve whenever practical:

* Existing APIs
* Existing ATS behavior
* Existing ATC behavior
* Existing automation behavior
* Existing signaling behavior
* Existing save formats
* Existing configuration formats
* Existing workflows

Refactoring is allowed.

Rewriting is allowed.

Breaking ATSAssistMod content requires explicit approval.

When modernizing ATSAssistMod functionality:

* Preserve user workflows
* Preserve map compatibility
* Preserve signal compatibility
* Preserve automation compatibility

Prefer compatibility layers over breaking migrations.

---

## Rewrite Rules

Large-scale rewrites are allowed.

Valid reasons include:

* Performance improvements
* Bug reduction
* Technical debt removal
* Kotlin migration
* Improved maintainability
* Better NeoForge integration

Rewrites should preserve whenever practical:

* User-facing behavior
* Pack compatibility
* Save compatibility
* Networking compatibility
* Existing workflows

A rewrite is successful when users only notice:

* Better performance
* Better stability
* Additional functionality

and do not experience behavioral regressions.

---

## Performance Expectations

Performance improvements are encouraged.

When choosing between:

* identical behavior with better performance
* identical behavior with lower memory usage
* identical behavior with fewer bugs

prefer the improved implementation.

Performance regressions require justification.

---

## Kotlin Migration Rules

Kotlin migration must be incremental.

Do not perform repository-wide Java-to-Kotlin rewrites.

Convert only the smallest reasonable scope necessary for the requested task.

Prefer adding Kotlin alongside Java when that reduces risk.

### Existing Kotlin Interop

The following Kotlin utilities expose Java-compatible APIs:

* UnitConverter
* PackTextDecoder

Preserve:

* `@file:JvmName(...)`
* Existing Java call sites
* Existing interoperability patterns

### Public API Stability

Preserve whenever practical:

* Public APIs
* Mod IDs
* Registry IDs
* Resource locations
* Packet IDs
* Save-data formats
* Configuration formats

---

## NeoForge Requirements

Always preserve:

* DeferredRegister behavior
* Registry timing
* Event bus registration timing
* Lifecycle ordering
* Client/server separation
* Dedicated server compatibility
* Networking semantics

Do not introduce lifecycle regressions.

---

## ATSA Project Compatibility

The `atsa` project depends on the root project.

Changes must not unnecessarily break:

* Existing compile-time APIs
* Existing interoperability
* Existing integrations

Preserve Java interoperability whenever practical.

---

## Architecture Guidance

### General

Keep changes localized.

Avoid unrelated refactors.

Avoid modifying root entry points unless necessary.

Prefer feature-local implementations.

### Feature Development

Before implementing:

* Train systems
* Rail systems
* Vehicle systems
* Signal systems
* ATS systems
* ATC systems
* WebCTC systems
* Script systems
* Installed object systems

inspect existing implementations first.

Follow existing package boundaries whenever practical.

### Root Entry Point

Keep modifications to:

`RealTrainModRenewed.java`

minimal.

Prefer:

* Registration helpers
* Feature-specific modules
* Dedicated registries

---

## Build Requirements

### Standard Build

Unix:

```bash
./gradlew build
```

Windows:

```powershell
.\gradlew.bat build
```

### Validation

Whenever possible:

1. Build the project.
2. Review warnings.
3. Review errors.
4. Validate affected modules.

If only `atsa` is modified, still consider validating the root project.

### Network Restrictions

If dependency downloads are blocked:

* Report the limitation.
* Request approval if necessary.
* Do not attempt workarounds.

### Reporting

Always report:

* Commands executed
* Build status
* Validation status
* Commands that could not be executed

---

## AI Development Workflow

### Primary Implementation Agent

CodeWhale (DeepSeek) is the primary implementation agent.

Responsibilities:

* Coding
* Refactoring
* Rewrites
* Kotlin migration
* Documentation
* Bug fixes
* Architecture changes

CodeWhale should perform implementation work unless explicitly instructed otherwise.

### Reviewer Agent

Codex is a review-only agent.

Codex should:

* Review changes
* Review architecture
* Review compatibility risks
* Review performance concerns
* Review maintainability concerns

Codex should not be the primary implementation agent unless explicitly requested.

---

## Agent Separation Principle

Implementation agents must never review their own work.

The reviewer must be a separate agent instance with an isolated context window.

Independent review is preferred over self-review.

---

## Review Subagent Requirements

Use a dedicated Codex review subagent.

The review subagent should receive only:

* User request
* Git diff
* Modified files
* Build output
* Test output

The review subagent should not receive:

* Internal implementation reasoning
* Historical planning context
* Unrelated repository files

Objectives:

* Independent review
* Reduced context contamination
* Reduced token usage
* Better bug detection

---

## Review Scope

Review for:

* Logic errors
* Regression risks
* Kotlin interoperability
* Java interoperability
* NeoForge lifecycle correctness
* Save-data compatibility
* Networking compatibility
* Resource compatibility
* Thread-safety concerns
* Performance concerns
* Maintainability concerns

Avoid style-only comments unless they provide meaningful value.

---

## Review Workflow

After implementation:

1. Generate Git diff.
2. Build when possible.
3. Launch Codex review subagent.
4. Evaluate findings.
5. Fix critical issues.
6. Rebuild if necessary.
7. Report results.

Completion reports should include:

* Summary of changes
* Build status
* Review findings
* Remaining risks

---

## Token Usage Optimization

To reduce token consumption:

* Do not send the entire repository to the reviewer.
* Prefer Git diffs over full files.
* Send only changed files.
* Send only directly related dependencies.
* Avoid repository-wide audits unless explicitly requested.

---

## Tooling Requirements

### CodeWhale

Primary implementation tool.

Windows path:

`C:\Users\rinny\AppData\Local\Programs\CodeWhale\bin\codewhale.exe`

### CodeRabbit

Do not use CodeRabbit for this repository.

---

## Generated Files

Do not commit:

* build/
* out/
* run/
* generated/
* temporary artifacts

unless explicitly requested.

---

## General Coding Standards

* Keep changes focused.
* Prefer compatibility.
* Minimize risk.
* Preserve behavior whenever practical.
* Favor maintainability over cleverness.
* Avoid unrelated refactors.
* Avoid unnecessary breaking changes.
* Document non-obvious decisions.
* Follow existing project architecture unless there is a strong reason not to.

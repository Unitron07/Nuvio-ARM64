# Nuvio-ARM64 docs

Design notes and validation records for the Windows ARM64 fork. Start with the
first group. The stage records are kept as history; later stages supersede
earlier status lines in them.

## Current reference

| Doc | Covers |
|---|---|
| [WINDOWS_ARM64_SURFACE.md](WINDOWS_ARM64_SURFACE.md) | Architecture, native dependencies and how to build on Windows ARM64 |
| [STAGE6_ARM64_RELEASES.md](STAGE6_ARM64_RELEASES.md) | Release ledger, version numbering, build procedure and open release gates |
| [STAGE6_UPDATER.md](STAGE6_UPDATER.md) | How the in-app update check finds fork releases |
| [STAGE6_UPSTREAM_SYNC.md](STAGE6_UPSTREAM_SYNC.md) | Upstream sync automation and conflict rules |
| [STAGE6_SURFACE_VALIDATION.md](STAGE6_SURFACE_VALIDATION.md) | Installer validation on a Surface |
| [FIRST_MSI_CANDIDATE.md](FIRST_MSI_CANDIDATE.md) | First MSI candidate, encrypted delivery and owner approval steps |

## Stage history

| Stage | Docs |
|---|---|
| 3: app integration | [STAGE3_ARM64_VALIDATION.md](STAGE3_ARM64_VALIDATION.md), [STAGE3_AUTH_INVESTIGATION.md](STAGE3_AUTH_INVESTIGATION.md) |
| 4: touch and performance | [STAGE4_ROADMAP.md](STAGE4_ROADMAP.md), [STAGE4_TOUCH_VALIDATION.md](STAGE4_TOUCH_VALIDATION.md), [STAGE4_VERTICAL_SCROLLING.md](STAGE4_VERTICAL_SCROLLING.md), [STAGE4_PERFORMANCE.md](STAGE4_PERFORMANCE.md), [STAGE4_FINAL_TOUCH_CLEANUP.md](STAGE4_FINAL_TOUCH_CLEANUP.md), [STAGE4_FINAL_VALIDATION.md](STAGE4_FINAL_VALIDATION.md) |
| 5: performance and decoding | [STAGE5_ARM64_PERFORMANCE.md](STAGE5_ARM64_PERFORMANCE.md), [STAGE5_HARDWARE_DECODING.md](STAGE5_HARDWARE_DECODING.md), [STAGE5_SURFACE_VALIDATION.md](STAGE5_SURFACE_VALIDATION.md) |
| 6: releases, updater and sync | See "Current reference" above |

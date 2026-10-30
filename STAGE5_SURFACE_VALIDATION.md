# Stage 5: exact Surface owner procedure

Acceptance remains pending until physical results are recorded. Use the development
image and `stage5-owner-tools.zip` from the same PR head SHA. The image's
STAGE3_IMAGE.json sourceCommit must equal tools scripts/windows/stage5/SOURCE.json.
The launcher enforces this match. No credentials or commercial media belong in CI.

## Preparation and launch

Extract image and tools into separate new directories. Install Node.js (the collector
uses built-in modules only) and Python if generating the existing raw AVI fixture.
Use native ARM64 Windows. From the extracted owner-tools directory, in PowerShell:

```powershell
# ImageDirectory is the directory containing Nuvio.exe and STAGE3_IMAGE.json.
./scripts/windows/stage5/Start-Stage5Test.ps1 `
  -ImageDirectory C:/NuvioStage5/image/Nuvio `
  -RunDirectory C:/NuvioStage5/runs/h264-auto
# Close the application normally to finish collection.
# Reopen the SAME isolated profile for persistence checks:
./scripts/windows/stage5/Start-Stage5Test.ps1 `
  -ImageDirectory C:/NuvioStage5/image/Nuvio `
  -RunDirectory C:/NuvioStage5/runs/h264-auto -Resume
```

The Stage 3 isolation/audit/manifest infrastructure is reused. No personal profile
is copied. Console output is discarded unless explicitly requested through the
older fixture-only launcher. The Stage 5 launcher enables native diagnostic files
and process metrics; raw data stays in private/. Profile/ and private/ stay local.
The Stage 3 ZIP is separate historical acceptance output. For Stage 5 share only
`stage5-safe-results.json` after inspecting it, plus a manually redacted checklist.
Regenerate after additional sessions with:

```powershell
node ./scripts/windows/stage5/collect-results.mjs C:/NuvioStage5/runs/h264-auto
```

## Authorized media and fixture

Use owner-created/authorized local files, or generate synthetic media with FFmpeg
from [its documented lavfi sources](https://ffmpeg.org/ffmpeg-filters.html#testsrc_002c-rgbtestsrc_002c-yuvtestsrc_002c-testsrc2_002c-colorspectrum_002c-colorchart_002c-allrgb_002c-allyuv).
Example with an FFmpeg build supporting libx264:

```powershell
ffmpeg -f lavfi -i 'testsrc2=size=1920x1080:rate=30' `
  -f lavfi -i 'sine=frequency=440:sample_rate=48000' -t 120 `
  -c:v libx264 -pix_fmt yuv420p -c:a aac synthetic-h264-1080p.mp4
Get-FileHash ./synthetic-h264-1080p.mp4 -Algorithm SHA256
node ./scripts/windows/stage3/fixture.mjs --port 8765 --media ./synthetic-h264-1080p.mp4
```

Install the loopback addon printed by the fixture in the isolated app; select its
direct or protected stream. The fixture serves the supplied bytes under an AVI-named
route: container sniffing is expected; record the actual MP4 container, not that route.
Use local media; do not expose the loopback fixture publicly. The fixture's fixed
synthetic headers exercise the existing propagation path. For HEVC/AV1/VP9 and 4K,
generate corresponding authorized variants with available encoders; record encoder,
profile/pixel format/fps and hash. Do not label SDR testsrc with HDR metadata and call
that valid HDR; use a properly authored authorized HDR sample. Add an owner-created
subtitle and known sync cue; a sine alone cannot quantify A/V synchronization.

## Required sessions

An optional standalone libmpv check is included. It requires a full native ARM64
JDK 21 (the app's trimmed runtime cannot launch Java source), the image's JNA JAR,
and the pinned runtime DLL from the matching development-evidence artifact:

```powershell
& C:/Jdk21/bin/java.exe -cp C:/NuvioStage5/image/Nuvio/app/jna-5.19.1.jar `
  ./scripts/windows/stage5/MpvDecoderProbe.java `
  C:/NuvioStage5/evidence/build/windows-media-arm64/runtime/libmpv-2.dll `
  C:/NuvioStage5/media/synthetic-h264-1080p.mp4 auto 30 C:/NuvioStage5/probe-auto.jsonl
```

Substitute the actual JNA filename in app/ (it may include a hash). Use a new output
path each time; repeat with `no` for software control. The probe opens its own video
window, mutes output through null audio and loops the authorized local file. It
preserves the ARM64 script-disable preset. It is a decoder investigation tool,
not an application lifecycle/fullscreen/audio/progress acceptance substitute.

For each codec/resolution in STAGE5_HARDWARE_DECODING.md, record settings, hardware
driver, case hash and requested/current mode. Default/automatic first, then existing
software preference, with NVIDIA enhancement off. Use a new run directory for each
condition. A software result for unsupported hardware is acceptable if visible
playback/fallback works. Check frame rates, bit depth and containers separately.

Run ten consecutive **actual player** sessions in one application launch. Each:
open, play for at least 30 seconds, pause, seek forward/back, resume, enter/exit
fullscreen, close player, return to catalog and wait a fixed idle interval. Verify
all ten native session IDs have initialization/destruction evidence and record
memory/handle trends. A CI libmpv cycle is not this UI workflow.

Then run an authorized 60–120 minute file with periodic seek/pause/fullscreen checks;
record sync drift, drops, buffering, resource trends and normal exit. For failures use
fixture failure stream, invalid local bytes, unsupported profile and a deliberate
fixture stop/restart. Confirm error UI, return navigation, and a successful next
stream. Record decoder-init failure separately if reproducible; do not manufacture
a driver failure by changing global production options.

For newly generated progress: choose a previously unwatched synthetic case, note
starting position, watch several minutes, stop, exit, relaunch same RunDirectory with
-Resume and verify the new position. Sign into the owner's account manually only in
the isolated profile if desired; verify new progress synchronization on another
trusted client. Existing Continue Watching data does not satisfy this test. Never
put tokens/account identifiers in result text.

## Owner checklist

- [ ] Native launch/normal exit, exact source SHA and architecture recorded.
- [ ] H.264/HEVC 1080p/4K; AV1/VP9 where supported; mode evidence recorded.
- [ ] Default software fallback visibly works; unknown properties are not passes.
- [ ] Seeking/fullscreen, subtitles/audio and A/V sync checked.
- [ ] Ten actual player sessions and settled memory/handle trends recorded.
- [ ] 60–120 minute playback evaluated; errors/recovery and exit checked.
- [ ] CPU/memory sampled; Video Decode/3D GPU evidence or limitation recorded.
- [ ] Controlled power/battery protocol completed where feasible.
- [ ] Newly generated progress survives restart; new sync tested separately.
- [ ] Account/addon sync, real debrid and correct headers remain functional.
- [ ] Stage 4 touchscreen, sources/sidebar, keyboard/mouse/trackpad remain functional.
- [ ] Review stage5-safe-results.json; share no profile, raw logs, dumps or credentials.

Stage 5 stays draft until owner acceptance and final-head CI pass. No merge/release
is authorized. Physical acceptance cannot be inferred from a successful build.

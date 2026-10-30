# Stage 5: Windows ARM64 decoder investigation

Status: instrumentation implemented; short standalone Snapdragon D3D11VA evidence
recorded below. Full embedded application acceptance remains pending.
Baseline: main `2f71f1c8a4585457427c540ec2de3cc260768d72`, merged Stage 4 PR #9.

## Findings and plan

The real application player is `composeApp/src/desktopMain/native/windows/player_bridge.cpp`,
not the older vendor Media Foundation player. Kotlin NativePlayerController passes source,
headers and decoder preference through JNI. The bridge embeds an mpv-owned GPU output
in a child HWND, with separate WebView2 controls and its own native UI/event threads.
Source headers use the Stage 3 typed node array. Preserve that path and lifecycle.

Existing normal configuration requests `vo=gpu-next,gpu,`, `gpu-api=auto`, `hwdec=auto`,
and `hwdec-codecs=all`. Software preference selects `hwdec=no`; hardware priority
disables software fallback, while the default preference enables fallback. NVIDIA
super-resolution has its own D3D11/NVIDIA path; it is not a Qualcomm preset.
Unified-memory detection also selects the existing fast profile. No new GPU policy
or quality/cache/gesture optimization is justified without comparative measurements.
An unused `hwdecLogged` member was not functioning decoder evidence.

Plan: instrument the actual bridge, test privacy/property handling, increase the
existing native HTTP lifecycle smoke to ten cycles, package matching owner tools,
then measure the codec matrix and power on the owner's device. Do not change the
working defaults merely because acceleration has not yet been measured.

The pinned shinchiro ARM64 runtime is mpv `e471496c56f0f49c133bd88b50aed62b845f1909`
with FFmpeg `9a861e9687d30e657d59521817cad18405995983`; archive provenance and
hashes remain in `scripts/windows/build-media-runtime.ps1`. Native architecture and
successful playback establish neither decoder availability nor active acceleration.

## Evidence levels

1. `hwdec` is the requested policy, not the current decoder.
2. A supported decoder list/build option or successful option assignment shows
   capability/configuration, not decoding on a particular GPU.
3. `hwdec-current` reports the active hardware decoder, `no` means software;
   unavailable means no loaded decoder/unknown, not software.
4. For actual playback evidence, require a recognized hardware `hwdec-current`
   across progressing, unpaused, non-idle playback samples with decoded video
   dimensions; inspect the visible video and corroborate with Video Decode engine
   activity or an ETW GPU trace where available. Seeking alone is not playback.

The collector counts these progressing intervals per player session. This is runtime
evidence, not a per-frame GPU trace or proof that every frame used the same path.
It rejects seek-sized jumps and unavailable/paused/idle samples. `hwdec-interop`
alone and general GPU/3D activity would not establish decoding, and are not used as
proof. Software after an automatic request may mean unsupported profile, missing
driver/decoder or runtime fallback; the reason is not exposed by these properties.
The bridge records `not-exposed`, never invents a reason from generic playback errors.

## D3D11 and frame transfer

Upstream supports D3D11VA with gpu/gpu-next and compatible D3D11/ANGLE context;
`d3d11va-copy` downloads frames to system memory. A normal D3D11VA path can still
copy GPU surfaces for rendering; hardware decoding is not a zero-copy guarantee.
The default automatic API does not prove D3D11 was selected. Do not globally pin
D3D11, disable profile checks or enable experimental zero-copy based on assumptions
about Snapdragon. Inspect actual driver/runtime output before changing configuration.

Sources pinned to the bundled runtime:
- [Hardware options and copy paths](https://github.com/mpv-player/mpv/blob/e471496c56f0f49c133bd88b50aed62b845f1909/DOCS/man/options.rst)
- [Runtime properties, tracks and unavailable values](https://github.com/mpv-player/mpv/blob/e471496c56f0f49c133bd88b50aed62b845f1909/DOCS/man/input.rst)

## Privacy and lifecycle

Diagnostics are disabled unless `NUVIO_PLAYBACK_DIAGNOSTICS` is an absolute file path.
The owner launcher sets a new path in its isolated private run directory. Once per
second, the existing mpv event thread queries a fixed property set. No extra thread
or polling exists when disabled. Numeric unavailable/nonfinite values become null;
codec/decoder/pixel/hardware values use exact allowlists, unknown values become
`other`. No raw logs, titles, paths, URLs, metadata, headers or account fields are
exported. Disk retention stops at 16 MiB per file. File/property failures cannot
interrupt playback. Source commit is joined from the checked image/session manifest.

`initialized` means libmpv initialization/load command returned, not first frame.
`destroy-returned` means libmpv destruction returned, not proof all WebView2/native
resources are leak-free. A missing marker can be disabled/failed IO as well as a hang.
The existing ARM64 Lua/script disable preset, UI Automation initialization, controls,
fullscreen, header propagation, DLL ownership and P2P unsupported guard are preserved.

## Short physical standalone decoder results (October 30, 2026)

On this Microsoft Surface Pro 11, Snapdragon X1P64100, Qualcomm Adreno X1-85 driver
31.0.170.0, Windows ARM64 10.0.26300.0 and Microsoft ARM64 JDK 21.0.12.1+1,
the pinned libmpv DLL (SHA256 `f90cee7e3dbb0a12fbfa6b95af1fb2bb22dbfebf8422efc0324918ee75661850`)
reported **d3d11va during progressing playback for all eight generated codec/resolution
cases** below. This is actual runtime decoder evidence, not an option/availability
inference. [Sanitized conditions, hashes and interval summaries](docs/stage5/standalone-surface-results.json)
identify the exact probe source commit.

| Synthetic SDR 8-bit 4:2:0, approximately 30 fps | 1080p | 3840x2160 |
| --- | --- | --- |
| H.264 | d3d11va; 12 progressing intervals | d3d11va; 6 intervals |
| HEVC | d3d11va; 6 intervals | d3d11va; 6 intervals |
| AV1 | d3d11va; 6 intervals | d3d11va; 6 intervals |
| VP9 | d3d11va; 5 intervals | d3d11va; 6 intervals |

Each standalone probe ran 10 seconds (H.264 1080p 15 seconds), looping four-second
clips (H.264 1080p ten seconds). Generated lavfi testsrc2 was encoded locally through
libmpv/FFmpeg using libx264/libx265 ultrafast/crf28, libsvtav1 preset12/crf45 and
libvpx-vp9 realtime/cpu-used8. No third-party movie was used or committed. Audio output
was null. GPU output used its own visible native window (`gpu-next,gpu,`, automatic
GPU API), not Nuvio's embedded HWND/WebView2. Visual quality/audio/sync/HDR acceptance
is not asserted. Some probes sampled VO drop counts up to 1–3 (software control up
to 4); loop resets mean last-minus-first is not a cumulative total. Background
compilation/encoding and very short clips preclude performance comparisons.

The same H.264 1080p file with `hwdec=no` progressed with `hwdec-current=no`.
Generated raw AVI with automatic hardware mode also progressed with `no`, demonstrating
software selection for a non-hardware codec. This does not inject a failing hardware
decoder or prove recovery from every driver error. Embedded application telemetry,
long sessions, audio, profile/bit-depth/HDR matrix and power remain pending.

## Full application codec matrix: physical acceptance pending

| Codec | 1080p | 4K | Extra cases |
| --- | --- | --- | --- |
| H.264/AVC | pending | pending | 24/30/60 fps, 8-bit; uncommon profiles separately |
| HEVC | pending | pending | Main/Main10, SDR/HDR, 24/30/60 fps |
| AV1 | pending | pending where supported | 8/10-bit, film grain separately |
| VP9 | pending | pending where supported | profiles 0/2, 8/10-bit |

For every row record file hash, locally assigned case ID, codec/profile, dimensions,
fps/bit depth/container, requested/current decoder, visible playback, A/V sync,
subtitle/audio selection, seeking, fullscreen, failures and default-mode software
fallback. No supported codec/profile is asserted from the chip name or CPU architecture.
Existing Stage 3/4 owner reports establish MKV video/audio and real debrid playback,
not a codec/hardware matrix. Headless raw AVI software CI is explicitly separate.

## Native interoperability audit

QuickJS-KT remains pinned to `a081755bcdb18ee6afa7ffee25b8fa976f06d31f`.
Inspection of `quickjs/native/jni/mapping/js_value_to_jobject.c` shows collection
conversion loops calling Java List.add/Set.add/Map.put without consistently checking
exceptions before further JNI calls. A thrown allocation/callback/conversion exception
can therefore leave a pending exception across JNI operations. This is a correctness
risk, not merely cosmetic warning output. Several ownership/error paths cross helper
boundaries; adding checks only to silence `-Xcheck:jni` would not prove safe cleanup.
Defer a separately scoped source-copy patch with thrown-callback, allocation/error,
collection conversion and cleanup tests; retain the current exception/recovery suite
on ARM64 and x64. No working engine/native ownership is rewritten in Stage 5.

The player checks/clears Java callback exceptions, joins native threads with bounded
detach fallback and closes WebView2 on its UI thread. A detached, wedged worker may
still outlive shutdown; destroy-returned cannot prove that path safe. Native stress
and owner memory/handle observations are required. Do not infer leak freedom from
unit controller fakes or the 30 empty libmpv initialization cycles.

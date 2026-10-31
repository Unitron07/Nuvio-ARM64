# Native dependency provenance and unresolved distribution review

Application: GPLv3; upstream LICENSE and vendor compose-media-player LICENSE are
retained. JDK runtime legal/ and existing JAR META-INF notices are preserved.
QuickJS-KT a081755bcdb18ee6afa7ffee25b8fa976f06d31f, QuickJS
04be246001599f5995fa2f2d8c91a0f198d3f34c and c-vector
774773d4cb1e66dd736e90e7f482d4f22af464c6 license files/provenance are copied from
the verified source build. Vulkan-Loader/Headers notices are kept in separate
directories to avoid filename collisions. WebView2 SDK 1.0.4078.44 LICENSE and
NOTICE are copied from the hash-verified NuGet package; no WebView2 browser
runtime is silently downloaded or bundled by the MSI.

mpv source: https://github.com/mpv-player/mpv/tree/e471496c56f0f49c133bd88b50aed62b845f1909
FFmpeg source: https://github.com/FFmpeg/FFmpeg/tree/9a861e9687d30e657d59521817cad18405995983
The accompanying mpv Copyright/GPL/LGPL and FFmpeg LICENSE/COPYING files come from
these exact immutable source revisions. These texts do NOT prove which optional
codecs/static dependencies were enabled or satisfy the entire corresponding-source
obligation on their own. Builder recipe:
https://github.com/shinchiro/mpv-winbuild-cmake/tree/1f8d2e76255abc48f0456d9be99b4cab9c6566e6
Archive SHA256: 015f133399b3525021b6d937cf54ace2761d4f70224d5e62f5df24191b28f398.

Outstanding: complete enabled static dependency graph and exact revisions, build
options/scripts/patches, applicable notices, and complete corresponding-source
availability/redistribution review for this ARM64 libmpv build and dependencies.
Upstream's x64 binary distribution does not establish coverage of this separate
ARM64 distribution. Architecture/closure validation is not licensing clearance.
The MSI remains encrypted for the owner; public distribution is not authorized.

Build-only tools: WiX 4.0.6 package (MS-RL), source commit
73c897383236ddbbbc6ba257634013c1269ceec2; age 1.3.2 (BSD-3-Clause),
https://github.com/FiloSottile/age/tree/v1.3.2. Their archive hashes are recorded in
BUILD-PROVENANCE.json. Their executables/private keys are not installed into the
application payload. Source/build provenance and inventory are evidence, not an
assertion that every public-release obligation has been closed.

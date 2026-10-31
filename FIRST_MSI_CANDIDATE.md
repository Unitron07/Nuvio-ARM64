# First native Windows ARM64 MSI candidate

## Selected immutable basis

| Field | Proposed first candidate |
|---|---|
| Ledger key | alpha32-r1 |
| Upstream release | 0.1.32-alpha, release ID 420833706 |
| Resolved release commit | da6910dbca048b34c1ba7205fba8a4e6c01dc65e |
| Application source | 086ec17bc8d4e3d01bb868ce7e05af0d519cfb04 (merged Stage 6 PR #17) |
| Human version | 0.1.32-alpha+arm64.1 |
| MSI version / sequence | 1.0.1 / 1 |
| MSI filename | Nuvio-ARM64-Windows-arm64-0.1.32-alpha+arm64.1.msi |
| Display / manufacturer | Nuvio-ARM64 / Nuvio-ARM64 contributors |
| Scope | per-user, LocalAppData/Programs/Nuvio-ARM64 |
| Permanent UpgradeCode | febc83be-f241-4a78-8f6c-10af5e358acc |
| Distribution | unsigned, owner-encrypted test only |

The 0.1.32 release commit is an ancestor of merged main; 0.1.33-alpha is NOT.
No 0.1.33 changes are claimed. Ledger was empty and the release feed contained no
fork releases at allocation. Preparation checks these facts against current
remote state; it never fakes ancestry. The packaging recipe is independently
pinned to the approved main workflow SHA and recorded beside the application SHA.
That separation allows an installer fix without relabeling application code or
using a self-referential ledger commit. All output manifests identify the same
application source plus the exact recipe, run ID, version and byte hashes.

## Upstream comparison and targeted fixes

Inspected upstream Dev 3e876f699e59e38b66ccc738331fa8071cbf29cc and official
release 0.1.33-alpha on 2026-10-30. The downloaded official MSI matched its
GitHub SHA256 a60574364e2b1a253b62a0a93a7e9c6ba38971c3f2e36c10cc913cb8987cee75;
Windows Get-AuthenticodeSignature returned NotSigned. Its metadata is Nuvio /
Nuvio Media, per-machine ALLUSERS=1, x64;1033, numeric version 1.1.33, UpgradeCode
395990ee-9b8a-3548-922c-e7a23a495b8d. The upstream workflow has Windows MSI
administrative extraction, contents manifest, signature-status recording and
release SHA256SUMS; signing/notarization credentials/steps observed are for macOS,
not Windows. No upstream Windows signing credential is reused or assumed.
The fork follows unsigned-development/checksum/extracted-content verification
conventions, retaining its already-agreed per-user identity. No signed publisher
or SmartScreen trust claim is made. No upstream signing identity is impersonated.

The app remains GPLv3. Upstream release assets contain installers, hashes and the
GitHub source archives; the inspected top-level tree contains LICENSE but no
complete bundled-native corresponding-source archive was established. Our ARM64
mpv/FFmpeg binary uses a separate source/builder chain; upstream x64 practices do
not clear it. Known license texts/notices are installed under app/licenses;
full static dependency/source closure remains open in NATIVE-NOTICES.md. No
publicDistributionCleared/security/signing/automaticInstall policy flag is enabled.

Upstream's Supabase SDK default session persistence and DesktopStorage properties
remain compatible. Files/Windows Preferences are not an encrypted credential
vault; anonymous-user storage is not a password store, while sessions/debrid and
other provider tokens need separate at-rest review. This task makes no unrelated
authentication rewrite or profile migration. The existing fork informational
updater is safer than inherited unchecked download/installer execution and stays
disabled for automatic installation. Separate Windows-user isolation is mandatory.

The actual packaging defect is OpenJDK jpackage's Windows WixPipeline hard-coded
x86/x64 architecture switch, including JDK21. We retain its tested native ARM64
app-image and author only its MSI container using hash-pinned WiX4 -arch arm64.
No x64 launcher, foreign JVM or architecture-check suppression is used. The
container has no custom action scripts and removes only its own files, empty
installation/menu folders and fork HKCU registration. No profile directory is
removed. The native fixture also exposed a COM metadata-reader defect: View.Execute()
returned an empty output item into property results; suppressing that return
ensures identity checks compare scalar strings. Exact extracted payload bytes,
MSI identity/Arm64 scope, PE/JAR resources,
packaged JVM, notices and extracted native startup are checked. Physical install
and rollback remain separate owner tests.

## Access control and immutable attempts

[GitHub documents](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/download-workflow-artifacts)
that signed-in repository readers can download Actions artifacts. The public
repository's ordinary upload is not owner-only, and an approval environment does
not change download ACLs. At the owner's explicit preference, candidate uploads
contain only an age-encrypted bundle plus non-sensitive metadata/checksums. The
owner private identity was generated on this PC and has an owner-only file ACL;
only its public recipient enters the ledger/CI. No PAT, decryption key or personal
profile is supplied to a runner. Candidate-mode bridge/image uploads are disabled
so the release job does not expose an unencrypted companion payload. Ordinary
PR development-image behavior is retained.

Once a candidate is dispatched, its version is reserved. Reruns and later
same-candidate dispatches fail closed and require a new reviewed revision/MSI
sequence. Preserve the original MSI and checksum; never silently replace bytes
under one human/package version. Failed attempts are retained as evidence.

## Required owner actions and current status

This PR is draft. It must not be merged without owner authorization. It does not
build the real application MSI from an unmerged ledger or bypass the main-only
protected environment. PR fixture CI creates an unrelated disposable packaging
probe and ephemeral encryption key; it is not the first Nuvio MSI and is never
installed or uploaded as an application candidate.

After review and owner-authorized merge, run windows-arm64-release.yml from main
with candidate_id alpha32-r1, then Review deployments → arm64-internal-candidate →
Approve and deploy. Do not disable the reviewer or run on an unapproved branch.
Native integration/QuickJS then gate the image/MSI build. The draft release remains
metadata-only. No binary is attached to a GitHub release and there is no publisher.

The first actual MSI digest, byte inventory, recipe SHA, validation report and
encrypted artifact URL exist only after that approved run succeeds. They are not
claimed in this preparation PR. Source integration is satisfied for the chosen
basis; ledger merge and owner environment approval are the remaining build gates.

Exact decrypt/install/uninstall/rollback and second-candidate instructions are in
[scripts/windows/stage6/FIRST_MSI_SURFACE_TEST.md](scripts/windows/stage6/FIRST_MSI_SURFACE_TEST.md).
Preserve the local private key separately; never put it in the GitHub PR or test
results. Public legal/security/trust and real hardware installation gates remain
open. No public release or successful physical installation is claimed.

References: [JDK21 WixPipeline](https://github.com/openjdk/jdk21u/blob/master/src/jdk.jpackage/windows/classes/jdk/jpackage/internal/WixPipeline.java),
[WiX architecture option](https://docs.firegiant.com/wix/tools/wixexe/),
[upstream workflow](https://github.com/NuvioMedia/NuvioDesktop/blob/3e876f699e59e38b66ccc738331fa8071cbf29cc/.github/workflows/desktop-release.yml).

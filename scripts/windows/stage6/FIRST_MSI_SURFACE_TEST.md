# First MSI — isolated Surface Pro 11 validation

This is an unsigned owner-only test, not a public release. Use a separate
**disposable standard Windows user account** on Surface Pro 11 / native Windows
ARM64. Do not sign into your normal Nuvio account/profile in your normal Windows
session for this test. The installed app keeps `%APPDATA%/Nuvio` and SDK Windows
Preferences: fork installer identity does not isolate production profile data.

## Obtain and decrypt the exact candidate

1. The owner must first merge the reviewed candidate PR, then open repository
   Actions → Windows ARM64 approved internal candidate → Run workflow → branch
   main → candidate_id `alpha32-r1`. Open the new run → Review deployments →
   select `arm64-internal-candidate` → Approve and deploy. Unitron07 is the
   required reviewer. Do not disable that protection. No dispatch is started by
   merely opening a PR. Do not re-run a candidate version: use a new reviewed
   revision and MSI sequence for any subsequent attempt.
2. After a successful run, download `windows-arm64-owner-encrypted-installer`.
   It contains only `.age` ciphertext, BUILD-SUMMARY.json and a ciphertext
   checksum. Confirm source `086ec17bc8d4e3d01bb868ce7e05af0d519cfb04`, upstream
   `0.1.32-alpha`, human version `0.1.32-alpha+arm64.1`, MSI `1.0.1` / sequence 1,
   matching run ID and the approved packaging recipe SHA. No 0.1.33 inclusion is
   claimed. Verify the encrypted file with Get-FileHash -Algorithm SHA256 against
   the summary/checksum before decrypting it.
3. The age identity is saved only on the owner's PC at:
   `C:/Users/Husi/Documents/Codex/2026-10-30/files-pasted-by-the-user-nuvio/outputs/first-msi-private/owner-age-key.txt`.
   Keep this file private and make an owner-controlled offline backup; never
   upload it, paste its contents, commit it, or include it in test evidence. Only
   the public recipient is in the ledger. An owner testing on another PC must
   transfer the private key through a private owner-controlled channel.
4. Use age v1.3.2 for your host (Windows ARM64 archive SHA256
   `fae351336c8d5f30f93cc3ccdec16d1ed7851d68cdd6656d41955cc31a447c9d`;
   Windows x64 archive SHA256
   `f48d8f8f9ebe903ab5027ed067652f2cc1db94bc206976430133b905dcd8e8c7`).
   Obtain the exact release from https://github.com/FiloSottile/age/releases/tag/v1.3.2,
   verify its archive checksum, and extract it. In a fresh private download folder:

```powershell
& 'C:\NuvioMsiTest\age\age.exe' --decrypt `
  -i 'C:\path\to\owner-age-key.txt' `
  -o 'C:\NuvioMsiTest\candidate.zip' `
  'C:\NuvioMsiTest\Nuvio-ARM64-Windows-arm64-0.1.32-alpha+arm64.1.msi.test-bundle.zip.age'
if ($LASTEXITCODE -ne 0) { throw 'Decryption failed; do not install partial output.' }
Expand-Archive 'C:\NuvioMsiTest\candidate.zip' 'C:\NuvioMsiTest\candidate'
Get-FileHash 'C:\NuvioMsiTest\candidate\Nuvio-ARM64-Windows-arm64-0.1.32-alpha+arm64.1.msi' -Algorithm SHA256
Get-AuthenticodeSignature 'C:\NuvioMsiTest\candidate\Nuvio-ARM64-Windows-arm64-0.1.32-alpha+arm64.1.msi'
```

Compare the MSI digest to SHA256SUMS and BUILD-PROVENANCE.json. Expect NotSigned;
there is no upstream publisher certificate or SmartScreen trust claim. Keep the
original MSI and checksum unchanged. Decryption authenticates ciphertext; a
checksum does not establish a trusted publisher. If bytes/identity differ, stop.

## Install in the disposable Windows account

Sign out of your normal Windows account and sign into the disposable test user.
Copy only the decrypted candidate test folder into that account through an
owner-controlled local location. Do not copy any normal Nuvio profile/session.
Run interactively, with a private local installer log:

```powershell
$msi='C:\NuvioMsiTest\candidate\Nuvio-ARM64-Windows-arm64-0.1.32-alpha+arm64.1.msi'
Start-Process msiexec.exe -ArgumentList @('/i', ('"' + $msi + '"'), '/l*v', '"C:\NuvioMsiTest\install-private.log"') -Wait
```

The MSI uses standard Windows Installer progress UI and default per-user path
`%LOCALAPPDATA%/Programs/Nuvio-ARM64`. It does not request an x64 launcher, copy a
ZIP into your normal profile, or need files from a development extraction.
If Windows offers only an unsupported-architecture error, or the install needs
unexpected elevation, stop and record a sanitized error code. Do not disable
system trust controls globally. Choose to run an unsigned test only after
personally verifying its provenance and deciding to accept that test risk.

Check each item separately and record pass/fail, source SHA, Windows build, MSI
version/sequence and sanitized symptoms:

- Installer opens and identifies Nuvio-ARM64; completes without errors.
- Windows Installed Apps contains one Nuvio-ARM64 entry; fork Desktop/Start Menu
  shortcuts work and official Nuvio registration/shortcuts remain intact.
- Launch from the installed shortcut after moving the downloaded ZIP/test-image
  folders away. Verify normal startup and native ARM64 runtime prerequisites.
- Enter credentials privately in the disposable account. Verify authentication,
  account/addon synchronization, catalogs and existing Continue Watching behavior.
- Verify real video/audio, D3D11VA evidence versus fallback, pause, seeking,
  fullscreen, subtitles, track/player controls and clean exit without crashes.
- Repeat vertical/horizontal/expanded-shelf/Discover/stream-selector touch
  scrolling, sidebar navigation and player-menu dismissal checks.
- Update checking queries Unitron07/Nuvio-ARM64 releases, safely handles an empty
  feed, and never offers an x64 installer. In-app installation stays disabled.

Native torrent/P2P remains unsupported. CI extraction/startup is not physical
installation, account/provider playback, hardware decoding or touch acceptance.
Capture only manual check results/error codes or carefully redacted screenshots;
exclude emails, passwords, tokens, profile files, provider URLs and raw logs.

## Uninstall, reinstall and rollback

Close Nuvio-ARM64, then Settings → Apps → Installed apps → Nuvio-ARM64 → Uninstall.
Confirm the fork entry/shortcuts disappear and official Nuvio remains. In this
disposable test account only, confirm `%APPDATA%/Nuvio` and expected SDK test
preferences are preserved. Reinstall the SAME verified MSI and test restoration.
Never delete a production profile to make a test pass. Keep raw installer logs
private; report only error code and sanitized symptoms.

For the FIRST installer there is no previous fork MSI to restore. If it fails,
uninstall only the fork in the disposable account and return to the previously
validated development image using its packaged isolated owner launcher. The
normal Windows account/profile must remain untouched. Cancelled/failed MSI
transactions should leave the prior installation intact; verify instead of
assuming rollback succeeded.

## Exact future second-candidate upgrade test

Allocate a NEW ledger entry, same upstream basis and permanent UpgradeCode
`febc83be-f241-4a78-8f6c-10af5e358acc`, revision 2, human version
`0.1.32-alpha+arm64.2`, MSI sequence 2 / product version `1.0.2`, unless already
allocated. Pin its actual approved source and packaging recipe. Run it through
owner approval and all CI gates. Never replace candidate 1 with new bytes.

In the SAME disposable Windows account, privately back up its test-only data,
install candidate 1, create test state, close the app, then install candidate 2
normally. Confirm one fork uninstall entry, version 1.0.2, unchanged permanent
UpgradeCode, preserved test data and full playback/touch checks. Preserve both
MSIs/checksums. If rollback is necessary, close apps, uninstall candidate 2,
install the original verified candidate 1 and restore only the test-account
backup. Do not force a numeric downgrade over a live profile. No successful
upgrade is claimed until these two real versions have been tested on hardware.

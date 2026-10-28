# Stage 3 authentication investigation — October 28, 2026

## Official deployment configuration recovered

The official upstream `NuvioMedia/NuvioDesktop` release **0.1.33-alpha** Linux
package was inspected without executing it. Its SHA256 matches the upstream
SHA256SUMS.txt entry:
`15bac6dbc6b1e4291b7e53dcc95a054774559c2fad2d38810b14cf7f3f84caf1`.
The packaged SupabaseConfig uses **https://api.nuvio.tv** and an embedded public
anonymous JWT (`role=anon`, `iss=supabase`), not a service-role key.
GET `/auth/v1/settings` with that anonymous key returned **HTTP 200**, without an
account login. Both required repository variables have now been configured from
those exact upstream values; the key is not reproduced in documentation/logs.
This supersedes the earlier instruction to obtain deployment values manually.
The owner subsequently confirmed successful sign-in, account content and installed
addon synchronization, existing Continue Watching/resume data, real debrid playback
and real streams through the native ARM64 app. This resolves the earlier missing-
configuration issue for the tested login workflow. These are owner reports, not CI
tests. Exact account-test build SHA is unavailable: record a post-authentication-
fix build, without assigning these results to f7095b11 or another guessed commit.
Newly generated progress synchronization after restart, session-expiry/logout edge
cases and exhaustive HTTPS/provider cases remain unverified.

Source: https://github.com/NuvioMedia/NuvioDesktop/releases/tag/0.1.33-alpha

Stage 3 core functionality is validated and ready for merge in existing PR #4.
No merge or release is authorized here. The earlier local-fixture hardware results
below retain their **9604cd61** provenance; the later owner account report is separate.
Authentication implementation credit: d955a86d wired build inputs and safe missing-
configuration diagnostics; f7095b11 documented recovery and repository configuration
of the exact upstream public deployment values. Neither establishes the owner-test SHA.

## Historical findings before the authentication fix

The owner established DNS/TCP/HTTPS connectivity to api.nuvio.tv. This does not
establish a working authentication request or session.

The fork generates SupabaseConfig from NUVIO_SUPABASE_URL and
NUVIO_SUPABASE_ANON_KEY at build time; both default to empty strings.
The ARM64 development-image workflow supplied neither. Repository variable and
secret name listings were empty during this investigation. The upstream Dev
release workflow requires both values, and its SupabaseProvider still uses the
same configured Supabase-compatible client. Merely seeing api.nuvio.tv in server
discovery policy does not configure that client.

This establishes a build-configuration defect and a deterministic pre-network
failure path. The earlier credentialed failure was not independently
reproduced; later owner testing confirms the corrected workflow succeeds. No claim is made that fixing configuration alone proves account login.
Inspection of the downloaded 89a208f3 app JAR with javap also confirmed both
packaged constants are empty (only empty/nonempty booleans were reported).
The passing CI image for the owner's reported **9604cd61** commit was subsequently
downloaded and inspected: both backend URL and publishable-key constants are
empty there too. `Stage3-auth-packaged-config-check.json` records only those
booleans. This confirms the missing configuration in the matching CI build,
without collecting the owner's local profile or attempting a real login.
No evidence currently identifies an ARM64 TLS, WebView2 or native-player defect.
Email/password sign-in calls the SDK directly; it does not use browser callbacks.

## Changes

- ARM64 image build accepts the three upstream authentication configuration names
  from repository variables or secrets (fallback URL optional).
- Client creation rejects missing URL/key with a fixed, actionable error.
- Email sign-in failure logging emits stage, hostname, HTTP status when available,
  and exception class, without throwable
  messages, stack traces, request URLs, credentials or tokens.
- Synthetic regression tests exercise missing and present configuration.

## Owner hardware evidence

Owner-reported for **9604cd61**: native prerequisites pass; 34 native files pass
architecture verification; exit 0, no recorded crashes. Normal application launch,
UI rendering, settings/navigation and clean close pass; one session lasted about
1 minute 44 seconds with no crash report.

Local addon manifest/catalog/metadata/streams returned 200, redirects 302, protected
media 206 with Range and required authorization, user-agent, referer and extra
fixture headers. Owner's MKV playback includes video and audio, pause/resume,
forward/back seeking, fullscreen, return to application, repeated playback and
the touchscreen interactions tested. These reports do not establish ten-session
stress, horizontal catalog gestures, full catalog scrolling or leak freedom.

## Stage 4 touch tracking

Four owner-reported usability defects are tracked in the [Stage 4 roadmap](STAGE4_ROADMAP.md):
[Home kinetic momentum #5](https://github.com/Unitron07/Nuvio-ARM64/issues/5),
[details touch scrolling #6](https://github.com/Unitron07/Nuvio-ARM64/issues/6),
[Settings content scrolling #7](https://github.com/Unitron07/Nuvio-ARM64/issues/7), and
[subtitle menu dismissal #8](https://github.com/Unitron07/Nuvio-ARM64/issues/8).
Shared/upstream desktop paths are involved; ARM64 specificity is not established.
These are deferred Stage 4 work, not Stage 3 core merge blockers.

## Safe retest procedure

1. Use a passing ARM64 CI image with the already configured upstream public
   deployment values. The owner does not need to discover authentication values.
   For local source builds, supply the documented build inputs privately; never
   substitute service-role keys, passwords or session tokens.
2. Build the ARM64 image and owner-tools artifacts from the same commit. Record
   STAGE3_IMAGE.json sourceCommit. Start a fresh isolated RunDirectory.
3. Owner enters their own credentials privately. If sign-in fails, report only:
   build SHA, whether the fixed missing-configuration message appeared, exception
   class from an independently reviewed/redacted excerpt, and the last completed
   stage. Do not share raw logs, account email, passwords, cookies or tokens.
4. Sign-in, synchronized account/addons/existing resume data and real debrid
   playback are now owner-confirmed. Any further tests of restart restoration,
   newly generated progress, expired sessions, logout and additional HTTPS/provider
   cases must be recorded separately; do not infer them from CI or earlier passes.

The owner ZIP collector whitelists generated session/result/fixture summaries and
excludes profile/private trees and raw logs. Existing privacy regression remains
required. Desktop credential-at-rest limitations remain a separate security task.

All five workflows passed at f7095b11 (including ARM64/x64 integration and QuickJS,
ARM64 image and architecture). Final-head status is recorded in PR #4. No real
account was used in automated tests.

Local final ARM64 regression build passed **103 tests in 19 suites**, with no
failures, errors or skips. Node fixture and owner archive privacy regressions
pass. Configuration inspection of the matching 9604cd61 image reports both
required values empty. Later real-account login and synchronization are owner-confirmed as described
above, independently of these synthetic configuration tests. Expired sessions,
restart restoration and logout are not promoted to passes.

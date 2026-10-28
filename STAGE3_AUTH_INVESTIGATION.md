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
Successful real-account login and session restoration still require owner testing.

Source: https://github.com/NuvioMedia/NuvioDesktop/releases/tag/0.1.33-alpha

Continue draft PR #4. No merge or release. Owner hardware results below apply to
image **9604cd61**, not subsequent commits.

## Findings

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
failure path. The owner's credentialed failure has not been independently
reproduced. No claim is made that fixing configuration alone proves account login.
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

Settings requires dragging the narrow scrollbar; normal content-area touch drag
does not scroll. Subtitle selection has no reliably discovered touch-only dismiss
method; Escape was needed. Settings uses shared Compose LazyColumn; player menu
logic is shared desktop JavaScript with Escape handling. These paths are not
ARM64-specific, but upstream hardware reproduction is still needed before calling
either issue confirmed inherited behavior. Record as two scoped Stage 4 defects;
no broad touch changes are included here.

## Safe retest procedure

1. Obtain the official deployment URL and **publishable/anonymous client key**
   from the upstream deployment maintainer. Do not use a service-role key, account
   password or session token. Configure matching repository variable/secret names;
   do not paste values into this chat or logs. Do not assume a root website URL
   is the correct authentication service base.
2. Build the ARM64 image and owner-tools artifacts from the same commit. Record
   STAGE3_IMAGE.json sourceCommit. Start a fresh isolated RunDirectory.
3. Owner enters their own credentials privately. If sign-in fails, report only:
   build SHA, whether the fixed missing-configuration message appeared, exception
   class from an independently reviewed/redacted excerpt, and the last completed
   stage. Do not share raw logs, account email, passwords, cookies or tokens.
4. Verify sign-in, synchronization, session restoration after restart and logout.
   These remain pending, along with real addons/debrid, progress persistence and
   full UI HTTPS. Do not infer success from backend connectivity or CI.

The owner ZIP collector whitelists generated session/result/fixture summaries and
excludes profile/private trees and raw logs. Existing privacy regression remains
required. Desktop credential-at-rest limitations remain a separate security task.

Base CI at 73d7c78c passed all five workflows. New authentication-change CI must
be checked separately before using its image. No real account was used in tests.

Local final ARM64 regression build passed **103 tests in 19 suites**, with no
failures, errors or skips. Node fixture and owner archive privacy regressions
pass. Configuration inspection of the matching 9604cd61 image reports both
required values empty. Real-account login, synchronization, expired sessions,
restart restoration and logout remain owner acceptance tests; these are not
promoted to passes by the configuration tests.

# Stage 3 authentication investigation — October 28, 2026

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
The owner's exact 9604cd61 image was not inspected; its image workflow is unchanged.
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

# Stage 4 final touchscreen cleanup

Continue draft PR #9; no merge or release until owner acceptance.

## Owner observations

The owner reports physical Surface Pro 11 success for short vertical swipes,
improved real-catalog vertical scrolling with shared artwork reuse, smooth
horizontal shelves, source/stream and Settings scrolling, subtitle/audio menu
dismissal, and real debrid playback. The exact tested commit was not supplied;
these observations are not acceptance of this follow-up build. Stage 4 is now in
final cleanup. Only expanded shelves, Discover genre scrolling and accidental
sidebar expansion remain in the latest reported scope.

## Causes and targeted changes

- Expanded catalog and folder grids use LazyVerticalGrid, separate from Home's
  list. Neither grid had the shared desktop promoted-pointer drag modifier.
  Both now pass their own LazyGridState to that modifier for vertical drag and
  framework fling. Tile clicks and wheel/keyboard handlers remain in place.
- Discover's filters use NuvioDropdownChip. Its desktop Material DropdownMenu
  had a popup-local ScrollState without the shared drag modifier. The menu now
  shares an explicit remembered ScrollState with the modifier. Existing option
  callbacks, focusable popup, outside dismissal and mobile sheet are retained.
- The adaptive sidebar expanded immediately on hover. Pointer entry alone,
  including a pointer left near Back when the root route returns, was enough.
  This explains an accidental expansion path; actual Back click propagation or
  overlapping hitboxes was not established. Entry, focus and click alone now do
  not expand it. Unpressed mouse movement beyond touch slop followed by 250 ms
  in the rail establishes hover intent. Exit/press/route departure cancels pending
  intent. Route departure clears transient expansion/profile state. A labeled
  expand/collapse chevron provides explicit touch, mouse and keyboard access.
  Permanent Expanded style remains expanded; Compact ignores hover as before.

The existing shared drag physics, image cache, player, authentication and provider
paths are unchanged. Windows may promote touch to Mouse; movement while pressed
cannot arm sidebar hover, but real hardware remains necessary to validate event
sequences. The chevron uses existing localized Expand/Collapse strings.

## Automated coverage and limits

TouchCleanupTest exercises grid dragging without item activation and subsequent
tap, actual dropdown dragging/selection, sidebar entry/click suppression,
deliberate mouse expansion, route departure/return reset and expansion policies.
The Stage 3 runner includes these alongside existing drag/fling, wheel, nested
scroll, source-list, menu/keyboard, image-cache, auth, playback and privacy checks.
Synthetic desktop input is not physical touchscreen acceptance. Check real popup
outside dismissal, keyboard traversal, rail geometry and Back on the Surface.

## Matching image and tools

Use final-head passing CI artifacts linked in PR #9:

1. `windows-arm64-development-evidence`: extract its app image to
   `C:/NuvioTests/Nuvio`, with the launcher and STAGE3_IMAGE.json directly there.
2. `stage3-integration-arm64`: extract its nested `stage3-owner-tools.zip` to
   `C:/NuvioTests/final-touch-tools`. This guide is included.
3. Confirm both artifact workflow revisions equal the final PR head. Check
   STAGE3_IMAGE.json.sourceCommit as well. Use a new run directory for this image.

In PowerShell from `C:/NuvioTests/final-touch-tools`:

```powershell
./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage4-final-touch -NativeSmoke
# Close the smoke session normally, then launch the isolated interactive session:
./scripts/windows/stage3/Start-OwnerTest.ps1 -ImageDirectory C:/NuvioTests/Nuvio -RunDirectory C:/NuvioTests/stage4-final-touch -Resume
```

If that directory already belongs to another image, choose a new name. Configure
your authorized addon/account privately in this isolated profile. Do not enable
fixture logging for real credentials. No new performance recording is required.

## Exact Surface retest (all pending for this build)

- [ ] Expand a long Home shelf. Drag vertically from blank space and directly
      over posters, using short/long strokes in both directions. Release during
      motion and confirm momentum. Drag must not open a title; a stationary tap
      must still open it. Return and verify position. Repeat an expanded folder.
- [ ] Discover: open a long genre list. Drag directly over options in both
      directions; verify motion/momentum without selection. Stop and tap an
      option; verify filter application and dismissal. Reopen and tap outside.
      Repeat with wheel/trackpad and keyboard arrows/Enter/Escape.
- [ ] Adaptive sidebar: tap navigation icons and move between tabs; entry or tap
      alone must not expand. Open a title near the rail and tap Back repeatedly
      with a finger; leave the pointer stationary in that area after return.
      Confirm no automatic expansion and that Back still works.
- [ ] Use the bottom chevron by touch, mouse and keyboard to expand/collapse.
      With a mouse, enter the rail, move deliberately inside it and pause;
      expansion should occur after about 250 ms. Exit should collapse hover
      expansion. Confirm Compact/Expanded preferences, profile controls, focus
      traversal and small-window geometry remain usable.
- [ ] Recheck short Home swipes, real-catalog vertical down/back scrolling,
      horizontal shelves, details, source selection, Settings, subtitle/audio
      dismissal, authorized debrid video/audio, account sync, mouse and keyboard.

Record image commit, Windows/display scaling, input type, each result and concise
reproduction details. Keep profiles, raw logs, account data and media URLs private.
Do not mark these new fixes physically passed until the owner reports results.

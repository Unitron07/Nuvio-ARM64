import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';

const source = readFileSync(new URL('../../composeApp/src/desktopMain/resources/player-ui/controls.js', import.meta.url), 'utf8');
function declaration(name) {
  const start = source.indexOf(`const ${name} =`);
  const end = source.indexOf('\n};', start);
  assert.ok(start >= 0 && end > start, `Missing ${name} declaration`);
  return source.slice(start, end + 3);
}
// Execute the production functions, without recreating the DOM or native player.
const { playbackShortcutCode, shortcutCommandForEvent } = vm.runInNewContext(
  `${declaration('playbackShortcutCode')}\n${declaration('shortcutCommandForEvent')}\n({playbackShortcutCode, shortcutCommandForEvent})`,
);
const event = values => ({ code: '', key: '', isComposing: false, metaKey: false, ctrlKey: false, altKey: false, shiftKey: false, ...values });
for (const key of [' ', 'Spacebar']) {
  for (const code of ['', 'Unidentified', 'Space']) {
    for (const type of ['keydown', 'keyup']) {
      assert.equal(playbackShortcutCode(event({ key, code, type })), 'Space');
    }
  }
}
for (const key of ['k', 'K']) {
  for (const code of ['', 'Unidentified', 'KeyK']) {
    assert.equal(shortcutCommandForEvent(event({ key, code })), 'keyboardToggle');
  }
}
for (const modifier of ['ctrlKey', 'altKey', 'metaKey']) {
  assert.equal(shortcutCommandForEvent(event({ key: 'k', [modifier]: true })), '');
}
assert.equal(playbackShortcutCode(event({ key: ' ', isComposing: true })), '');
assert.equal(shortcutCommandForEvent(event({ key: 'k', isComposing: true })), '');
assert.equal(shortcutCommandForEvent(event({ key: 'k', code: 'KeyK', isComposing: true })), '');
assert.equal(shortcutCommandForEvent(event({ key: 'k', code: 'KeyQ' })), '');
assert.equal(shortcutCommandForEvent(event({ key: 'a', code: 'KeyK' })), 'keyboardToggle');
assert.equal(shortcutCommandForEvent(event({ code: 'ArrowLeft' })), 'keyboardSeekBack');
assert.equal(shortcutCommandForEvent(event({ code: 'ArrowRight', shiftKey: true })), 'keyboardFineSeekForward');
assert.equal(playbackShortcutCode(event({ key: 'Enter' })), '');
console.log('PASS: physical and key-only playback shortcuts, modifiers and composition');

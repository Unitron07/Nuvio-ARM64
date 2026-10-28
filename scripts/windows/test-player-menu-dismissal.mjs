import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const root = new URL('../../composeApp/src/desktopMain/resources/player-ui/', import.meta.url);
const source = fs.readFileSync(new URL('controls.js', root), 'utf8').replace(/\r\n/g, '\n');
const start = source.indexOf('modalElements.forEach(modal => {\n  // Keep menu gestures');
const end = source.indexOf('\nsubtitleDelayMinus.', start);
assert.ok(start >= 0 && end > start);
let closed = 0, propagated = 0, prevented = 0;
const handlers = {}, buttonHandlers = {};
const modal = {classList: {contains: () => true}, addEventListener: (n, f) => handlers[n] = f,
  querySelectorAll: () => [{addEventListener: (n, f) => buttonHandlers[n] = f}]};
vm.runInNewContext(source.slice(start, end), {modalElements: [modal], closePlayerModal: notify => {assert.equal(notify, true); closed++;}});
const event = target => ({target, preventDefault: () => prevented++, stopPropagation: () => propagated++});
handlers.click(event({})); assert.equal(closed, 0); // panel content remains open
handlers.click(event(modal)); assert.equal(closed, 1); // backdrop
buttonHandlers.click(event({})); assert.equal(closed, 2);
handlers.pointerdown(event({})); assert.equal(closed, 2);
assert.equal(prevented, 1); assert.equal(propagated, 4);
const html = fs.readFileSync(new URL('controls.html', root), 'utf8');
assert.equal((html.match(/data-dismiss-player-menu/g) || []).length, 3);
console.log('PASS: track menu close/backdrop, panel retention and event isolation (synthetic DOM handlers)');

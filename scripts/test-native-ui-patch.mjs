import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const kotlin = fs.readFileSync(
  new URL('../app/src/main/java/com/ningmengchang/codexcompanion/web/NativeUiPatch.kt', import.meta.url),
  'utf8',
);
const match = kotlin.match(/val script: String = """\n([\s\S]*?)\n\s*"""\.trimIndent\(\)/);
assert.ok(match, 'NativeUiPatch Kotlin string was not found');
const lines = match[1].split('\n');
const indent = Math.min(...lines.filter((line) => line.trim()).map((line) => line.match(/^\s*/)[0].length));
const script = lines.map((line) => line.slice(indent)).join('\n');

let dialogOpen = false;
let observerCallback = null;
const states = [];
class MockMutationObserver {
  constructor(callback) { observerCallback = callback; }
  observe(target, options) {
    assert.equal(target, document.documentElement);
    assert.equal(options.attributes, true);
    assert.equal(options.subtree, true);
    assert.deepEqual([...options.attributeFilter], ['open']);
  }
}
const document = {
  documentElement: {},
  querySelector: (selector) => {
    assert.equal(selector, 'dialog[open]');
    return dialogOpen ? {} : null;
  },
};
const window = {
  CodexNativeUi: { setDialogOpen: (open) => states.push(open) },
};
const context = { window, document, MutationObserver: MockMutationObserver };

vm.runInNewContext(script, context, { filename: 'NativeUiPatch.js' });
assert.deepEqual(states, [false]);

dialogOpen = true;
observerCallback();
dialogOpen = false;
observerCallback();
assert.deepEqual(states, [false, true, false]);

vm.runInNewContext(script, context, { filename: 'NativeUiPatch-reinject.js' });
assert.deepEqual(states, [false, true, false, false]);

console.log('Native UI patch smoke test passed');

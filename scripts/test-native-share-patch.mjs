import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const kotlin = fs.readFileSync(
  new URL('../app/src/main/java/com/ningmengchang/codexcompanion/share/NativeSharePatch.kt', import.meta.url),
  'utf8',
);
const match = kotlin.match(/val script: String = """\n([\s\S]*?)\n\s*"""\.trimIndent\(\)/);
assert.ok(match, 'NativeSharePatch Kotlin string was not found');
const lines = match[1].split('\n');
const indent = Math.min(...lines.filter((line) => line.trim()).map((line) => line.match(/^\s*/)[0].length));
const script = lines.map((line) => line.slice(indent)).join('\n');

let originalFetchCalls = 0;
let nativeRequest = null;
const elements = {
  fileShareDialog: { open: true },
  shareDownloadButton: { download: '报告.pptx' },
  fileShareStatus: { textContent: '', dataset: {} },
};

class MockBlob {
  constructor(parts = [], options = {}) {
    this.size = parts.reduce((total, part) => total + String(part).length, 0);
    this.type = options.type || '';
  }
}

class MockResponse {
  constructor(body, options = {}) {
    this.bodyValue = body;
    this.status = options.status || 200;
    this.headers = options.headers || {};
  }

  async blob() {
    return this.bodyValue;
  }
}

const context = {
  Blob: MockBlob,
  DOMException,
  Math,
  Date,
  Promise,
  Response: MockResponse,
  URL,
  location: { href: 'http://127.0.0.1:3765/' },
  document: { getElementById: (id) => elements[id] || null },
  navigator: {},
};
context.window = {
  fetch: async () => {
    originalFetchCalls += 1;
    return new MockResponse(new MockBlob(['real']));
  },
  CodexNativeShare: {
    shareFile: (url, name, mime, requestId) => {
      nativeRequest = { url, name, mime, requestId };
    },
  },
};

vm.runInNewContext(script, context, { filename: 'NativeSharePatch.js' });
assert.equal(context.window.__codexNativeSharePatched, true);

const placeholder = await context.window.fetch('/api/artifacts/token-1/raw');
assert.equal((await placeholder.blob()).size, 0);
assert.equal(originalFetchCalls, 0, 'artifact bytes must not be fetched by JavaScript');
assert.equal(context.navigator.canShare({ files: [{}] }), true);

const sharePromise = context.navigator.share({ files: [{ name: '报告.pptx', type: '' }] });
assert.equal(nativeRequest.url, 'http://127.0.0.1:3765/api/artifacts/token-1/raw');
assert.equal(nativeRequest.name, '报告.pptx');
assert.equal(
  nativeRequest.mime,
  'application/vnd.openxmlformats-officedocument.presentationml.presentation',
);
context.window.__codexNativeShareResolve(nativeRequest.requestId);
await sharePromise;

elements.fileShareDialog.open = false;
await context.window.fetch('/api/artifacts/token-2/raw');
assert.equal(originalFetchCalls, 1, 'normal page fetches must remain untouched');

context.window.__codexNativeShareProgress('正在读取 50%');
assert.equal(elements.fileShareStatus.textContent, '正在读取 50%');

console.log('Native share patch smoke test passed');

import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const kotlin = fs.readFileSync(
  new URL('../app/src/main/java/com/ningmengchang/codexcompanion/web/NativeUiPatch.kt', import.meta.url),
  'utf8',
);

function extractScript(name) {
  const match = kotlin.match(new RegExp(`val ${name}: String = """\\n([\\s\\S]*?)\\n\\s*"""\\.trimIndent\\(\\)`));
  assert.ok(match, `NativeUiPatch.${name} Kotlin string was not found`);
  const lines = match[1].split('\n');
  const indent = Math.min(...lines.filter((line) => line.trim()).map((line) => line.match(/^\s*/)[0].length));
  return lines.map((line) => line.slice(indent)).join('\n');
}

const patchScript = extractScript('script');
const handleBackScript = extractScript('handleBackScript');
const observers = [];
let nativeSettingsRequests = 0;
const views = new Map();

function view(name, active = false) {
  const names = new Set(['view']);
  if (active) names.add('active');
  const item = {
    dataset: { view: name },
    classList: {
      contains: (value) => names.has(value),
      toggle: (value, enabled) => {
        if (enabled) names.add(value);
        else names.delete(value);
      },
    },
  };
  views.set(name, item);
  return item;
}

view('chat', true);
view('threads');
view('favorites');
view('projects');

function trigger(attributeName, target) {
  for (const observer of observers) {
    if (observer.options.attributeFilter.includes(attributeName)) {
      observer.callback([{ attributeName, target }]);
    }
  }
}

function setActiveView(name) {
  for (const [viewName, item] of views) item.classList.toggle('active', viewName === name);
  trigger('class', views.get(name));
}

const buttons = [...views.keys()].map((name) => ({
  dataset: { tab: name },
  click: () => setActiveView(name),
}));
const settingsDialog = {
  open: false,
  close() { this.open = false; },
};
const dialogs = [settingsDialog];
const appRoot = {};
const settingsBody = {
  children: [],
  append(child) { this.children.push(child); },
};
const filePopover = { hidden: true };
const fileDirectoryButton = {
  expanded: 'false',
  setAttribute(name, value) {
    if (name === 'aria-expanded') this.expanded = value;
  },
};
const projectUpButton = {
  hidden: true,
  disabled: false,
  clicks: 0,
  click() { this.clicks += 1; },
};
const fullscreenButton = {
  hidden: false,
  tabIndex: 0,
  attributes: {},
  style: {
    display: '',
    priority: '',
    setProperty(name, value, priority) {
      if (name === 'display') {
        this.display = value;
        this.priority = priority;
      }
    },
  },
  setAttribute(name, value) { this.attributes[name] = value; },
};

class MockMutationObserver {
  constructor(callback) {
    this.callback = callback;
    observers.push(this);
  }

  observe(target, options) {
    this.target = target;
    this.options = options;
  }
}

const document = {
  documentElement: {},
  fullscreenElement: null,
  webkitFullscreenElement: null,
  querySelector(selector) {
    if (selector === '.view.active') {
      return [...views.values()].find((item) => item.classList.contains('active')) || null;
    }
    if (selector === '#settingsSheet .settings-body') return settingsBody;
    throw new Error(`Unexpected querySelector: ${selector}`);
  },
  querySelectorAll(selector) {
    if (selector === 'dialog[open]') return dialogs.filter((dialog) => dialog.open);
    if (selector === '.bottom-nav button[data-tab]') return buttons;
    throw new Error(`Unexpected querySelectorAll: ${selector}`);
  },
  getElementById(id) {
    if (id === 'nativeConnectionSettingsButton') {
      return settingsBody.children.find((item) => item.id === id) || null;
    }
    return {
      app: appRoot,
      settingsSheet: settingsDialog,
      fileUploadPopover: filePopover,
      fileDirectoryButton,
      projectUpButton,
      fullscreenButton,
    }[id] || null;
  },
  createElement(tagName) {
    const listeners = new Map();
    return {
      tagName: tagName.toUpperCase(),
      addEventListener(type, listener) { listeners.set(type, listener); },
      click() { listeners.get('click')?.(); },
    };
  },
  exitFullscreen() {
    this.fullscreenElement = null;
  },
};
const window = {
  CodexNativeUi: { openConnectionSettings: () => { nativeSettingsRequests += 1; } },
};
const context = { window, document, MutationObserver: MockMutationObserver };

vm.runInNewContext(patchScript, context, { filename: 'NativeUiPatch.js' });
assert.equal(observers.length, 1);
assert.deepEqual([...observers[0].options.attributeFilter], ['class']);
assert.equal(fullscreenButton.hidden, true);
assert.equal(fullscreenButton.tabIndex, -1);
assert.equal(fullscreenButton.attributes['aria-hidden'], 'true');
assert.equal(fullscreenButton.style.display, 'none');
assert.equal(fullscreenButton.style.priority, 'important');
assert.equal(settingsBody.children.length, 1);
const nativeSettingsButton = settingsBody.children[0];
assert.equal(nativeSettingsButton.textContent, 'SSH 连接设置');
settingsDialog.open = true;
nativeSettingsButton.click();
assert.equal(settingsDialog.open, false);
assert.equal(nativeSettingsRequests, 1);

setActiveView('threads');
setActiveView('chat');
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(document.querySelector('.view.active').dataset.view, 'threads');
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(document.querySelector('.view.active').dataset.view, 'chat');

const dialog = {
  open: true,
  close() { this.open = false; },
};
dialogs.push(dialog);
assert.equal(vm.runInNewContext(handleBackScript, context), true);
assert.equal(dialog.open, false);

filePopover.hidden = false;
fileDirectoryButton.expanded = 'true';
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(filePopover.hidden, true);
assert.equal(fileDirectoryButton.expanded, 'false');

setActiveView('projects');
projectUpButton.hidden = false;
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(projectUpButton.clicks, 1);
projectUpButton.hidden = true;
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(document.querySelector('.view.active').dataset.view, 'chat');

document.fullscreenElement = {};
assert.equal(window.__codexNativeUiBack(), true);
assert.equal(document.fullscreenElement, null);

window.__codexNativeNavigationState.viewStack.length = 0;
window.__codexNativeNavigationState.currentView = 'chat';
assert.equal(vm.runInNewContext(handleBackScript, context), false);

vm.runInNewContext(patchScript, context, { filename: 'NativeUiPatch-reinject.js' });
assert.equal(settingsBody.children.length, 1, 'reinjection must not duplicate the settings entry');
assert.equal(observers.length, 1, 'reinjection must not install duplicate observers');

console.log('Native UI navigation patch smoke test passed');

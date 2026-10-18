import assert from 'node:assert/strict';
import fs from 'node:fs';

const root = new URL('../app/src/main/java/com/ningmengchang/codexcompanion/', import.meta.url);
const read = name => fs.readFileSync(new URL(name, root), 'utf8');
const main = read('MainActivity.kt');
// Structural contracts complement JVM policy tests; they are not device UI tests.
assert.equal((main.match(/registerForActivityResult\(/g) ?? []).length, 5);
assert.match(main, /override fun onDestroy\(\)[\s\S]*images\.dispose\(\)[\s\S]*if \(!isChangingConfigurations\)[\s\S]*webController\.destroy\(\)/);
assert.match(main, /existingTunnel\.isActive/);
const web = read('web/WebViewController.kt');
for (const bridge of ['CodexNativeShare', 'CodexNativeNotifications', 'CodexNativeUi']) assert.ok(web.includes(`"${bridge}"`));
for (const key of ['allowFileAccess = false', 'allowContentAccess = true', 'MIXED_CONTENT_NEVER_ALLOW', 'LOAD_DEFAULT']) assert.ok(web.includes(key));
assert.ok(web.includes('request.deny()'));
for (const file of ['config/ConnectionFormController.kt', 'web/ImageInputController.kt', 'download/DownloadPermissionController.kt', 'navigation/BackNavigationController.kt', 'share/ShareProgressController.kt', 'web/WebViewController.kt']) {
  assert.ok(!read(file).includes('registerForActivityResult('), `${file}: registration must remain Activity-owned`);
}
assert.ok(read('share/ShareRequestValidator.kt').includes('artifactRawPath.matches'));
console.log('Controller lifecycle/bridge/security wiring contracts passed (device validation still required).');

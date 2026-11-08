const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const scriptPath = path.join(__dirname, '../../main/assets/car-resume.js');

function page(id = 'video') {
    const listeners = {};
    class Media {
        constructor() { this.tagName = 'VIDEO'; this.paused = true; }
        play() { this.paused = false; return Promise.resolve(); }
        pause() { this.paused = true; }
    }
    const location = { pathname: '/watch', search: '?v=' + id };
    const window = { tubeRestorePausedVideoId: 'video' };
    const context = { window, location, URLSearchParams, HTMLMediaElement: Media,
        document: { addEventListener(type, listener) { listeners[type] = listener; } } };
    vm.runInNewContext(fs.existsSync(scriptPath) ? fs.readFileSync(scriptPath, 'utf8') : '', context);
    return { window, location, Media, listeners };
}

test('restored paused video rejects a later autoplay attempt', async () => {
    const p = page();
    const video = new p.Media();
    await video.play();
    assert.equal(video.paused, true);
});

test('explicit native Play releases the restored pause', async () => {
    const p = page();
    p.window.tubeReleaseRestorePause?.();
    const video = new p.Media();
    await video.play();
    assert.equal(video.paused, false);
});

test('trusted YouTube play button releases the restored pause', async () => {
    const p = page();
    p.listeners.click?.({ isTrusted: true, target: { tagName: 'BUTTON', closest: () => ({}) } });
    const video = new p.Media();
    await video.play();
    assert.equal(video.paused, false);
});

test('synthetic page click cannot silently resume the paused video', async () => {
    const p = page();
    p.listeners.click?.({ isTrusted: false, target: { tagName: 'BUTTON', closest: () => ({}) } });
    const video = new p.Media();
    await video.play();
    assert.equal(video.paused, true);
});

test('another video keeps normal playback', async () => {
    const p = page('another');
    const video = new p.Media();
    await video.play();
    assert.equal(video.paused, false);
});

test('autoplay attribute is stopped by the captured play event', () => {
    const p = page();
    const video = new p.Media();
    video.paused = false;
    p.listeners.play?.({ target: video });
    assert.equal(video.paused, true);
});

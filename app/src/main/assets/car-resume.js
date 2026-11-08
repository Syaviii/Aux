(() => {
    const id = window.tubeRestorePausedVideoId;
    if (!id || window.tubeReleaseRestorePause) return;
    let held = true;
    const holds = () => held && location.pathname === '/watch' &&
        new URLSearchParams(location.search).get('v') === id;
    window.tubeReleaseRestorePause = () => { held = false; };
    const play = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function (...args) {
        if (this.tagName === 'VIDEO' && holds()) {
            this.pause();
            return Promise.resolve();
        }
        return play.apply(this, args);
    };
    document.addEventListener('play', event => {
        if (event.target?.tagName === 'VIDEO' && holds()) event.target.pause();
    }, true);
    document.addEventListener('click', event => {
        if (!event.isTrusted) return;
        const target = event.target;
        if (target?.tagName === 'VIDEO' || target?.closest?.(
            '.ytp-play-button,.ytp-large-play-button,button[aria-label^="Play"]'))
            window.tubeReleaseRestorePause();
    }, true);
})();

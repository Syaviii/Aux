(() => {
    const id = window.tubeRestorePausedVideoId;
    if (!id || window.tubeReleaseRestorePause) return;
    let held = true;
    // The same keys the app uses: a YouTube id, "patreon:" and a Patreon post number, or a Twitch key
    const pageKey = () => {
        if (location.hostname.endsWith('twitch.tv')) {
            const video = location.pathname.match(/^\/videos\/(\d+)\/?$/);
            return video ? 'twitch:video:' + video[1] : null;
        }
        if (location.hostname.endsWith('patreon.com')) {
            const post = location.pathname.match(/^(?:\/[^/]+)?\/posts\/(?:[^/]*-)?(\d+)\/?$/);
            return post ? 'patreon:' + post[1] : null;
        }
        return location.pathname === '/watch' ? new URLSearchParams(location.search).get('v') : null;
    };
    const holds = () => held && pageKey() === id;
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

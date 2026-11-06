(() => {
    if (!window.nativecallbacks) return;
    window.tubeActiveVideo = () => {
        if (document.fullscreenElement?.tagName === 'VIDEO') return document.fullscreenElement;
        const videos = Array.from(document.querySelectorAll('video'));
        return videos.find(v => {
            const r = v.getBoundingClientRect();
            return r.width > 0 && r.height > 0 && r.bottom > 0 && r.top < innerHeight;
        }) || videos[0];
    };
    document.querySelectorAll('video').forEach(v => {
        if (v.dataset.tubeListeners) return;
        v.dataset.tubeListeners = '1';
        let nativeState = '', waitingTimer;
        const report = name => {
            if (v !== window.tubeActiveVideo() || name === nativeState) return;
            nativeState = name;
            window.nativecallbacks.onVideoEvent(name);
        };
        const clearWaiting = () => { clearTimeout(waitingTimer); waitingTimer = null; };
        v.addEventListener('playing', () => { clearWaiting(); if(window.tubeNativePlayback){v.pause();return;} report('playing'); });
        v.addEventListener('pause', () => { clearWaiting(); report('pause'); });
        v.addEventListener('ended', () => { clearWaiting(); report('ended'); });
        v.addEventListener('waiting', () => {
            if (waitingTimer) return;
            waitingTimer = setTimeout(() => {
                waitingTimer = null;
                let ahead = 0;
                for (let i = 0; i < v.buffered.length; i++) {
                    if (v.buffered.start(i) <= v.currentTime && v.buffered.end(i) >= v.currentTime)
                        ahead = v.buffered.end(i) - v.currentTime;
                }
                if (!v.paused && v.readyState < 3 && ahead < .5) report('waiting');
            }, 750);
        });
        if (window.tubeDebugPlayback) {
            let last = 0;
            const sample = () => {
                const now = Date.now();
                if (v !== window.tubeActiveVideo() || now - last < 2000) return;
                last = now;
                window.nativecallbacks.onPlaybackSample(JSON.stringify({time:v.currentTime,paused:v.paused,ready:v.readyState,width:v.videoWidth,height:v.videoHeight}));
            };
            v.addEventListener('timeupdate', sample);
            v.addEventListener('pause', sample);
        }
        if (v.paused) report('pause');
        else if (v.readyState >= 3) report('playing');
    });
})();

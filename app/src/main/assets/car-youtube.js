// YouTube's home grid laid out for the car screen: three videos a row with no empty ad slots, and the bottom bar
// (Shorts, Subscriptions, You) moved up beside the filter chips so accounts stay one tap away
(() => {
    if (window.__auxYoutubeCar) return;
    window.__auxYoutubeCar = true;
    const css = `
ytm-rich-item-renderer:has(> ad-slot-renderer),ytm-rich-section-renderer{display:none!important}
ytm-rich-grid-renderer > div{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr));column-gap:16px}
ytm-rich-grid-renderer > div > :not(ytm-rich-item-renderer){grid-column:1/-1}
ytm-rich-grid-renderer ytm-rich-item-renderer{width:auto!important;margin-left:0!important;margin-right:0!important}
ytm-pivot-bar-renderer{position:fixed!important;top:6px!important;bottom:auto!important;left:auto!important;right:16px!important;width:auto!important;height:36px!important;background:none!important;backdrop-filter:none!important;-webkit-backdrop-filter:none!important;box-shadow:none!important;border:0!important;display:flex!important;gap:8px;z-index:2000!important}
ytm-pivot-bar-item-renderer{flex:none!important;width:auto!important;min-width:0!important}
ytm-pivot-bar-item-renderer:has(.pivot-w2w){display:none!important}
.pivot-bar-item-tab{flex-direction:row!important;align-items:center!important;gap:6px!important;height:36px!important;padding:0 14px 0 10px!important;border-radius:18px!important;background:#272727!important;min-width:0!important}
.pivot-bar-item-tab[aria-selected=true]{background:#f1f1f1!important}
.pivot-bar-item-tab[aria-selected=true] *{color:#0f0f0f!important;fill:#0f0f0f!important}
.pivot-bar-item-tab c3-icon{width:20px!important;height:20px!important;margin:0!important}
.pivot-bar-item-title{font-size:14px!important;margin:0!important;line-height:1!important}
ytm-feed-filter-chip-bar-renderer .chip-bar-contents{clip-path:inset(0 var(--aux-pivot-w,210px) 0 0)}
html.aux-no-chips .page-container{padding-top:48px!important}
ytm-app{padding-bottom:0!important}
`;
    const add = () => {
        if (document.getElementById('aux-youtube-car')) return;
        const style = document.createElement('style');
        style.id = 'aux-youtube-car';
        style.textContent = css;
        (document.head || document.documentElement).appendChild(style);
    };
    add();
    document.addEventListener('DOMContentLoaded', add);
    // The chips stop where the moved buttons start, signed in there is one more button
    setInterval(() => {
        const bar = document.querySelector('ytm-pivot-bar-renderer');
        if (bar && bar.offsetWidth) document.documentElement.style.setProperty('--aux-pivot-w', (bar.offsetWidth + 24) + 'px');
        // Without the filter chips the moved buttons would sit on the page's own first row, the page moves down under them
        const chips = document.querySelector('ytm-feed-filter-chip-bar-renderer');
        document.documentElement.classList.toggle('aux-no-chips', !!(bar && bar.offsetWidth) && !(chips && chips.offsetHeight));
    }, 700);
})();

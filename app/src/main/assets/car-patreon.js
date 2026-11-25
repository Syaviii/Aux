// Patreon on the car screen: the home feed as a grid of videos and podcasts, and the login page laid out for the wide
// screen. Patreon's login page, with Patreon's own look kept: the sign-in buttons on the left,
// email on the right, and "Continue on your phone" added at the top. That one asks the phone to open Patreon's
// sign-in in Aux, the car loads the home page by itself once the phone is signed in
(() => {
    if (window.__auxPatreonCar) return;
    window.__auxPatreonCar = true;
    const PHONE_ICON = '<svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><rect x="6.5" y="2.5" width="11" height="19" rx="2.5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="12" cy="18" r="1.2" fill="currentColor"/></svg>';
    const place = (el, column, row) => { el.style.setProperty('grid-column', column, 'important'); el.style.setProperty('grid-row', row, 'important'); el.style.setProperty('margin', '0', 'important'); };
    const tidy = () => {
        if (!/^\/login/.test(location.pathname)) return;
        const apple = [...document.querySelectorAll('button')].find(b => /Continue with Apple/.test(b.innerText));
        if (!apple) return;
        let col = apple;
        while (col && !(getComputedStyle(col).display === 'flex' && col.children.length >= 6)) col = col.parentElement;
        if (!col || col.dataset.auxDone) return;
        const kids = [...col.children];
        const find = test => kids.find(test);
        const head = find(k => /Log in or sign up/i.test(k.innerText));
        const google = find(k => k.querySelector('iframe') || /Google/.test(k.innerText) && !/By clicking/.test(k.innerText));
        const appleRow = find(k => k.contains(apple));
        const facebook = find(k => /Continue with Facebook/.test(k.innerText));
        const or = find(k => k.innerText.trim() === 'or');
        const form = find(k => k.tagName === 'FORM' || k.querySelector('input[type=email]'));
        const help = find(k => /Need help/.test(k.innerText));
        const legal = find(k => /By clicking/.test(k.innerText));
        if (!head || !appleRow || !form) return;
        col.dataset.auxDone = '1';
        // The phone button is a copy of Patreon's own Apple button, so it matches the page exactly
        const phone = appleRow.cloneNode(true);
        const button = phone.querySelector('button');
        const label = [...button.querySelectorAll('*')].reverse().find(e => /Continue with Apple/.test(e.textContent) && !e.children.length) || button;
        const icon = button.querySelector('svg');
        if (icon) icon.outerHTML = PHONE_ICON;
        label.textContent = 'Continue on your phone';
        button.addEventListener('click', e => {
            e.preventDefault(); e.stopPropagation();
            if (window.nativecallbacks && window.nativecallbacks.onSignInOnPhone) window.nativecallbacks.onSignInOnPhone(location.href);
            label.textContent = 'Check your phone to sign in';
        }, true);
        col.insertBefore(phone, google || appleRow);
        col.style.setProperty('display', 'grid', 'important');
        col.style.setProperty('grid-template-columns', '400px 24px 400px', 'important');
        col.style.setProperty('column-gap', '24px', 'important');
        col.style.setProperty('row-gap', '12px', 'important');
        col.style.setProperty('justify-content', 'center', 'important');
        col.style.setProperty('align-content', 'start', 'important');
        col.style.setProperty('width', '872px', 'important');
        col.style.setProperty('max-width', 'none', 'important');
        for (let e = col.parentElement; e && e.tagName !== 'MAIN'; e = e.parentElement) { e.style.setProperty('max-width', 'none', 'important'); e.style.setProperty('width', 'auto', 'important'); e.style.setProperty('padding-top', '0', 'important'); e.style.setProperty('margin-top', '0', 'important'); e.style.setProperty('min-height', '0', 'important'); e.style.setProperty('justify-content', 'flex-start', 'important'); }
        col.style.setProperty('padding-top', '20px', 'important');
        col.style.setProperty('margin', '0 auto', 'important');
        place(head, '1 / -1', '1');
        head.style.setProperty('margin-bottom', '8px', 'important');
        place(phone, '1', '2');
        if (google) place(google, '1', '3');
        place(appleRow, '1', '4');
        if (facebook) place(facebook, '1', '5');
        if (or) { place(or, '2', '2 / 6'); or.style.setProperty('align-self', 'center', 'important'); or.style.setProperty('text-align', 'center', 'important'); }
        place(form, '3', '2 / 4');
        form.style.setProperty('align-self', 'start', 'important');
        if (help) { place(help, '3', '4'); help.style.setProperty('align-self', 'center', 'important'); }
        if (legal) place(legal, '1 / -1', '6');
    };
    // The home feed as a grid of cards: what can be watched or listened to comes first, posts the membership does not
    // include are dimmed rather than hidden. Built from Patreon's own feed data, a post opens on its normal page
    const FEED_CSS = `
#aux-feed{padding:8px 16px 24px;color:#fff;font-family:inherit}
#aux-feed .hd{display:flex;align-items:center;gap:8px;margin:2px 0 10px}
#aux-feed .hd b{font-size:20px;font-weight:700;margin-right:auto}
#aux-feed .chip{font-size:13px;font-weight:600;line-height:1;font-family:inherit;padding:8px 13px;border-radius:999px;background:#1f1f1f;color:#ddd;border:0}
#aux-feed .chip.on{background:#fff;color:#000}
#aux-feed .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px}
#aux-feed a.card{display:block;color:inherit;text-decoration:none;min-width:0}
#aux-feed .th{position:relative;aspect-ratio:16/9;border-radius:10px;overflow:hidden;background:#1f1f1f}
#aux-feed .th img{width:100%;height:100%;object-fit:cover;display:block}
#aux-feed .th .none{position:absolute;inset:0;display:flex;align-items:center;padding:14px;font-size:15px;font-weight:700;line-height:1.25;background:#262626}
#aux-feed .kind{position:absolute;left:6px;top:6px;background:#000c;border-radius:5px;padding:2px 7px;font-size:11px;font-weight:600}
#aux-feed .play{position:absolute;right:8px;bottom:8px;width:36px;height:36px;border-radius:50%;background:#fff;display:flex;align-items:center;justify-content:center;padding-left:2px}
#aux-feed .locked img{filter:brightness(.45)}
#aux-feed .lock{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;gap:6px;font-size:13px;font-weight:600}
#aux-feed .t{font-size:14px;font-weight:600;margin:7px 2px 2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
#aux-feed .m{display:flex;align-items:center;gap:5px;color:#aaa;font-size:12px;margin:0 2px;white-space:nowrap;overflow:hidden}
#aux-feed .m img{width:16px;height:16px;border-radius:50%;flex:none}
#aux-feed .msg{color:#aaa;font-size:14px;padding:24px 4px}
main.aux-feed-on > :not(#aux-feed){display:none!important}`;
    const PLAY = '<svg viewBox="0 0 24 24" width="20" height="20"><path d="M8 5v14l11-7z" fill="#000"/></svg>';
    const LOCK = '<svg viewBox="0 0 24 24" width="14" height="14"><path d="M7 10V8a5 5 0 0 1 10 0v2h1a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1zm2 0h6V8a3 3 0 0 0-6 0z" fill="#fff"/></svg>';
    const FILTERS = [['watch', 'Watch & listen'], ['play', 'Can play now'], ['all', 'All posts']];
    const media = p => /video|audio|podcast|live/.test(p.type);
    const feed = window.__auxFeed = window.__auxFeed || { posts: [], next: null, loading: false, done: false, failed: false, filter: null };
    try { feed.filter = feed.filter || localStorage.getItem('auxPatreonFilter') || 'watch'; } catch (e) { feed.filter = feed.filter || 'watch'; }
    const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
    const kindOf = p => /live/.test(p.type) ? 'Live' : /podcast/.test(p.type) ? 'Podcast' : /audio/.test(p.type) ? 'Audio' : /video/.test(p.type) ? 'Video' : /image/.test(p.type) ? 'Photo' : 'Post';
    const dateOf = iso => { const d = new Date(iso); if (isNaN(d)) return ''; const o = { month: 'short', day: 'numeric' }; if (d.getFullYear() !== new Date().getFullYear()) o.year = 'numeric'; return d.toLocaleDateString('en-US', o); };
    const pick = (o, keys) => { if (!o) return ''; for (const k of keys) if (o[k]) return o[k]; return ''; };
    const FIELDS = 'include=campaign&fields[post]=title,post_type,current_user_can_view,published_at,thumbnail,image,url&fields[campaign]=name,avatar_photo_url,avatar_photo_image_urls&page[count]=24&json-api-version=1.0';
    const load = async () => {
        if (feed.loading || feed.done) return;
        feed.loading = true;
        try {
            const r = await fetch(feed.next || '/api/stream?' + FIELDS, { credentials: 'include' });
            const j = await r.json();
            const camps = {};
            for (const c of j.included || []) if (c.type === 'campaign') camps[c.id] = { name: c.attributes.name, avatar: pick(c.attributes.avatar_photo_image_urls, ['thumbnail_small', 'thumbnail', 'default_small']) || c.attributes.avatar_photo_url };
            for (const p of j.data || []) {
                const a = p.attributes, c = camps[(((p.relationships || {}).campaign || {}).data || {}).id] || {};
                feed.posts.push({ id: p.id, title: a.title || 'Untitled post', type: a.post_type || '', open: !!a.current_user_can_view, date: a.published_at, url: a.url,
                    pic: pick(a.thumbnail, ['default_large', 'default', 'large', 'url']) || pick(a.image, ['large_url', 'url']), creator: c.name || '', avatar: c.avatar || '' });
            }
            feed.next = j.links && j.links.next || null;
            feed.done = !feed.next || !(j.data || []).length;
            feed.failed = false;
        } catch (e) { feed.failed = true; }
        feed.loading = false;
        render();
    };
    const shown = () => {
        const list = feed.filter === 'all' ? feed.posts : feed.filter === 'play' ? feed.posts.filter(p => p.open) : feed.posts.filter(media);
        // In the media views what can play right now leads
        return feed.filter === 'all' ? list : list.slice().sort((a, b) => (b.open - a.open) || (new Date(b.date) - new Date(a.date)));
    };
    const card = p => `<a class="card${p.open ? '' : ' locked'}" href="${esc(p.url)}"><div class=th>${p.pic ? `<img loading=lazy src="${esc(p.pic)}" alt="">` : `<div class=none>${esc(p.title)}</div>`}`
        + `<span class=kind>${kindOf(p)}</span>${p.open ? (media(p) ? `<span class=play>${PLAY}</span>` : '') : `<span class=lock>${LOCK}Paid members</span>`}</div>`
        + `<div class=t>${esc(p.title)}</div><div class=m>${p.avatar ? `<img src="${esc(p.avatar)}" alt="">` : ''}${esc(p.creator)}${p.creator ? ' · ' : ''}${dateOf(p.date)}</div></a>`;
    const render = () => {
        const box = document.getElementById('aux-feed');
        if (!box) return;
        const list = shown();
        box.querySelector('.grid').innerHTML = list.map(card).join('');
        for (const b of box.querySelectorAll('.chip')) b.classList.toggle('on', b.dataset.f === feed.filter);
        const msg = box.querySelector('.msg');
        msg.textContent = feed.failed ? 'Couldn\'t load your feed, scroll to try again' : feed.loading ? 'Loading…'
            : !list.length && feed.done ? (feed.filter === 'all' ? 'Nothing here yet, follow a creator to see their posts' : 'Nothing to watch here yet, try All posts') : '';
        // A filter that matches little would leave a short page, more is loaded until it fills or the feed ends
        if (!feed.loading && !feed.done && !feed.failed && box.scrollHeight < innerHeight * 2) load();
    };
    const feedPage = () => /^\/(home|feed)\/?$/.test(location.pathname);
    const showFeed = () => {
        const main = document.querySelector('main');
        if (!feedPage() || !main) { document.querySelectorAll('main.aux-feed-on').forEach(m => m.classList.remove('aux-feed-on')); return; }
        if (!document.getElementById('aux-feed-css')) { const s = document.createElement('style'); s.id = 'aux-feed-css'; s.textContent = FEED_CSS; document.head.appendChild(s); }
        if (main.querySelector(':scope > #aux-feed')) { main.classList.add('aux-feed-on'); return; }
        const box = document.createElement('div');
        box.id = 'aux-feed';
        box.innerHTML = `<div class=hd><b>Latest</b>${FILTERS.map(([k, n]) => `<button class=chip data-f="${k}">${n}</button>`).join('')}</div><div class=grid></div><div class=msg></div>`;
        box.addEventListener('click', e => {
            const open = e.target.closest('a.card');
            // A video or audio post the membership includes starts playing in Aux, anything else opens its page
            if (open) {
                const p = feed.posts.find(x => x.url === open.getAttribute('href'));
                if (p && p.open && media(p) && window.nativecallbacks && window.nativecallbacks.onPlayPicked) { e.preventDefault(); window.nativecallbacks.onPlayPicked(p.url); }
                return;
            }
            const chip = e.target.closest('.chip');
            if (!chip) return;
            feed.filter = chip.dataset.f;
            try { localStorage.setItem('auxPatreonFilter', feed.filter); } catch (x) {}
            render();
        });
        main.prepend(box);
        main.classList.add('aux-feed-on');
        if (!feed.posts.length && !feed.loading) load(); else render();
    };
    addEventListener('scroll', () => {
        const box = document.getElementById('aux-feed');
        if (box && feedPage() && innerHeight + scrollY > document.documentElement.scrollHeight - innerHeight) { if (feed.failed) feed.failed = false; load(); }
    }, { passive: true });

    let queued = false;
    const soon = () => { if (queued) return; queued = true; setTimeout(() => { queued = false; tidy(); showFeed(); }, 300); };
    const start = () => { tidy(); showFeed(); new MutationObserver(soon).observe(document.body, { childList: true, subtree: true }); };
    if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);
})();

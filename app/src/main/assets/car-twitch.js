// Twitch's mobile site laid out for the car screen: its logo bar and bottom tabs fold into one row at the top, so
// Browse, Activity, Profile and Log In stay one tap away and the streams get the height back. Twitch's class names
// change with every release, so its bars are found by the links inside them
(() => {
    if (window.__auxTwitchCar) return;
    window.__auxTwitchCar = true;
    const css = `
[data-aux=logo-row]{display:none!important}
/* Twitch slides its header away and back as the page scrolls, with the car's bursty touches that shows as the bar
   jumping up and down, the header stays put and solid instead */
[data-aux=head]{transform:none!important;transition:none!important;background:#0e0e10!important}
html,body{overflow-anchor:none!important}
[data-aux=nav]{top:0!important;bottom:auto!important;left:auto!important;right:12px!important;width:auto!important;height:53px!important;background:none!important;box-shadow:none!important;border:0!important;display:flex!important;align-items:center!important;gap:8px!important;z-index:3000!important;transform:none!important}
[data-aux=nav] > *{flex:none!important;width:auto!important}
[data-aux=nav] a{display:flex!important;flex-direction:row!important;align-items:center!important;gap:6px!important;height:38px!important;padding:0 16px 0 12px!important;border-radius:19px!important;background:#26262c!important;color:#efeff1!important;text-decoration:none!important}
[data-aux=nav] a > div,[data-aux=nav] a > div > div{display:flex!important;flex-direction:row!important;align-items:center!important;gap:6px!important;height:auto!important}
[data-aux=nav] a p,[data-aux=nav] a span{margin:0!important;font-size:15px!important;font-weight:600!important}
[data-aux=nav] a svg{width:20px!important;height:20px!important}
[data-aux=nav] a[href="/"]{display:none!important}
[data-aux=nav] a[data-aux-login]{background:#9146ff!important;font-size:15px!important;font-weight:600!important;padding:0 18px!important}
[aria-label^="Tag, "]{display:none!important}
/* Twitch's own search box is hidden, Aux's Search button searches Twitch */
header:has(input[type=search],input[placeholder=Search]) > div > div:has(input){visibility:hidden!important}
*:has(> * > [aria-label^="Tag, "]){display:none!important}
`;
    const add = () => {
        if (document.getElementById('aux-twitch-car')) return;
        const style = document.createElement('style');
        style.id = 'aux-twitch-car';
        style.textContent = css;
        (document.head || document.documentElement).appendChild(style);
    };
    const fixedOf = el => {
        for (let e = el; e && e !== document.body; e = e.parentElement) if (getComputedStyle(e).position === 'fixed') return e;
        return null;
    };
    const tidy = () => {
        add();
        const activity = document.querySelector('a[href="/activity"]');
        const logo = document.querySelector('a[aria-label="Go to the Twitch home page"]');
        const nav = activity && fixedOf(activity), head = logo && fixedOf(logo);
        if (!nav || !head || head === nav || head.children.length < 2) return;
        nav.dataset.aux = 'nav';
        head.dataset.aux = 'head';
        document.documentElement.style.setProperty('--aux-nav-w', (nav.offsetWidth + 24) + 'px');
        const row = head.children[0];
        if (!row.contains(logo)) return;
        const rowHeight = row.dataset.aux === 'logo-row' ? Number(row.dataset.auxHeight || 0) : row.offsetHeight;
        row.dataset.auxHeight = rowHeight;
        row.dataset.aux = 'logo-row';
        // The page keeps room above itself for the hidden logo row, that room goes back to the content
        const main = document.querySelector('main');
        if (main) {
            if (!main.dataset.auxPad) main.dataset.auxPad = parseFloat(getComputedStyle(main).paddingTop) || 0;
            main.style.setProperty('padding-top', Math.max(0, main.dataset.auxPad - rowHeight) + 'px', 'important');
        }
        // Signed out, the hidden row held Sign Up, Log In takes its place in the top row
        const signedOut = [...row.querySelectorAll('button,a')].some(b => /^(Sign Up|Log In)$/i.test((b.innerText || '').trim()));
        let login = nav.querySelector('a[data-aux-login]');
        if (signedOut && !login) {
            login = document.createElement('a');
            login.dataset.auxLogin = '1';
            login.href = '/login';
            login.textContent = 'Log In';
            nav.appendChild(login);
        } else if (!signedOut && login) login.remove();
    };
    let queued = false;
    const soon = () => { if (queued) return; queued = true; setTimeout(() => { queued = false; tidy(); }, 400); };
    const start = () => { tidy(); new MutationObserver(soon).observe(document.body, { childList: true, subtree: true }); };
    if (document.body) start(); else document.addEventListener('DOMContentLoaded', start);
})();

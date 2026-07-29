// Dark mode toggle. Dark is the default theme (js/themeInit.js sets it synchronously in
// <head> before this file loads, to avoid a flash of the wrong theme) - light only happens
// when explicitly chosen and stored. This file wires up the button click, keeps localStorage
// in sync, and re-syncs the theme when a page is restored from the back/forward cache (see
// applyStoredTheme below). The saved preference expires after 30 days (same as the
// "remember me" login cookie), same {v, exp} shape themeInit.js reads - both must agree.
var THEME_TTL_MS = 30 * 24 * 60 * 60 * 1000;

function applyStoredTheme() {
    try {
        var t = JSON.parse(localStorage.getItem('theme'));
        if (t && t.v === 'light' && t.exp > Date.now()) {
            document.documentElement.setAttribute('data-theme', 'light');
            return;
        }
    } catch (e) {
        // Malformed value - fall through to the dark default below.
    }
    document.documentElement.setAttribute('data-theme', 'dark');
}

document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('theme-toggle');
    if (!toggle) return;

    toggle.addEventListener('click', function () {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        var next = isDark ? 'light' : 'dark';
        document.documentElement.setAttribute('data-theme', next);
        localStorage.setItem('theme', JSON.stringify({ v: next, exp: Date.now() + THEME_TTL_MS }));
    });
});

// Mobile back-swipe (and the desktop back/forward buttons) often restore a page straight from
// the browser's in-memory back/forward cache instead of reloading it - the page reappears
// exactly as it was frozen, so a theme toggled *after* that snapshot was taken (e.g. on a post
// page you swiped forward to) never reaches the earlier page you swipe back to. pageshow with
// persisted=true fires specifically on that kind of restore, letting us re-apply whatever the
// theme actually is right now instead of showing the stale frozen state.
window.addEventListener('pageshow', function (event) {
    if (event.persisted) {
        applyStoredTheme();
    }
});

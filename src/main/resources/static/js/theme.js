// Dark mode toggle. The initial theme is applied by a tiny inline script in each page's
// <head> (before this file loads) to avoid a flash of the wrong theme on page load - this
// file only wires up the button click and keeps localStorage in sync afterward.
// The saved preference expires after 30 days (same as the "remember me" login cookie),
// same {v, exp} shape the inline head script reads - both need to agree on the format.
var THEME_TTL_MS = 30 * 24 * 60 * 60 * 1000;

document.addEventListener('DOMContentLoaded', function () {
    var toggle = document.getElementById('theme-toggle');
    if (!toggle) return;

    toggle.addEventListener('click', function () {
        var isDark = document.documentElement.getAttribute('data-theme') === 'dark';
        var next = isDark ? 'light' : 'dark';
        if (next === 'dark') {
            document.documentElement.setAttribute('data-theme', 'dark');
        } else {
            document.documentElement.removeAttribute('data-theme');
        }
        localStorage.setItem('theme', JSON.stringify({ v: next, exp: Date.now() + THEME_TTL_MS }));
    });
});

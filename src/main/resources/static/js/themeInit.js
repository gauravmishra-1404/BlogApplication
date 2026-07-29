// Runs synchronously in <head>, before any CSS/body renders, to set the theme with no flash
// of the wrong one. Was duplicated inline in 13 templates (DRY violation) - extracted here as
// a single shared, non-deferred <script src>, which still blocks rendering the same way an
// inline script would (same-origin, browser-cached after the first load).
//
// Dark is the default: unless a valid, non-expired {v:'light'} preference is stored, every
// page opens in dark mode. js/theme.js (loaded later, deferred) owns the actual toggle click
// and keeps this in sync; both must agree on the {v, exp} shape and the "light is the only
// stored exception" rule.
(function () {
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
})();

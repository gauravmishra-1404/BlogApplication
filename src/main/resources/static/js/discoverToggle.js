// Opens/closes the mobile-only "Discover" panel (trending tags + active writers, the same
// fragments/rightSidebar.html content that has its own dedicated column on desktop). Same
// open/close shape as js/dashboardFilterToggle.js and js/navProfileMenu.js - the panel itself
// is never removed from the layout flow, just hidden until toggled, so nothing needs
// repositioning. See the approved design artifact for why this replaced the old behavior
// (the widgets used to just fall to the very bottom of the feed, under every post).
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('discoverToggleBtn');
    var panel = document.getElementById('discoverPanel');
    if (!trigger || !panel) return;

    function close() {
        panel.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
    }

    function toggle(e) {
        e.stopPropagation();
        var next = panel.hidden;
        panel.hidden = !next;
        trigger.setAttribute('aria-expanded', String(next));
    }

    trigger.addEventListener('click', toggle);
    panel.addEventListener('click', function (e) { e.stopPropagation(); });
    document.addEventListener('click', function (e) {
        if (e.target === trigger || panel.contains(e.target)) return;
        close();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') close();
    });
});

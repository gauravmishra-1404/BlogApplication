// Opens/closes the sidebar's "More" overflow menu (fragments/sidebar.html) - the trigger/panel
// are only ever CSS-visible on the Shorts page's mobile top bar (dashboardStyle.css), so this
// script is only included there too, not on every page the shared sidebar fragment appears on.
// Same open/close shape as navProfileMenu.js's own profile dropdown - a second, independent
// instance, not a shared one, since both exist on the page at once.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('sidebarMoreBtn');
    var menu = document.getElementById('sidebarMoreMenu');
    if (!trigger || !menu) return;

    function close() {
        menu.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
    }

    function toggle(e) {
        e.stopPropagation();
        var next = menu.hidden;
        menu.hidden = !next;
        trigger.setAttribute('aria-expanded', String(next));
    }

    trigger.addEventListener('click', toggle);
    menu.addEventListener('click', function (e) { e.stopPropagation(); });
    document.addEventListener('click', close);
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') close();
    });
});

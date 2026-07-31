// Opens/closes the dashboard top bar's Filter panel (postDashboard.html). Same open/close
// shape as navProfileMenu.js, but the panel itself is never removed from the layout flow -
// it's a normal row above the feed, just hidden until toggled, so nothing needs repositioning.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('filterToggleBtn');
    var panel = document.getElementById('filterPanel');
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

// Opens/closes the header's profile dropdown (fragments/nav.html). The dark-mode row inside it
// keeps id="theme-toggle" so js/theme.js's existing click handler wires up unchanged - this
// file only owns the dropdown's own open/close, not the theme toggle itself.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('navAvatarTrigger');
    var dropdown = document.getElementById('navProfileDropdown');
    if (!trigger || !dropdown) return;

    function close() {
        dropdown.hidden = true;
        trigger.setAttribute('aria-expanded', 'false');
    }

    function toggle(e) {
        e.stopPropagation();
        var next = dropdown.hidden;
        dropdown.hidden = !next;
        trigger.setAttribute('aria-expanded', String(next));
    }

    trigger.addEventListener('click', toggle);
    dropdown.addEventListener('click', function (e) { e.stopPropagation(); });
    document.addEventListener('click', close);
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') close();
    });
});

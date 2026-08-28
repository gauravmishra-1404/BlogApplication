// Two jobs for the Shorts page's mobile top bar (fragments/sidebar.html) - only ever relevant
// there (dashboardStyle.css keeps everything below display:none on every other page/width), so
// this script is only included on shortsPage.html, not on every page the sidebar appears on.
//
// 1) Open/close the "More" overflow menu - same shape as navProfileMenu.js's own profile
//    dropdown, a second independent instance since both exist on the page at once.
// 2) Decide WHICH nav icons actually need to be in that menu, based on real available width
//    instead of a fixed always-collapsed list - a wide-enough phone shows every icon directly
//    and never shows "More" at all; only icons that would genuinely overflow move into the menu.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('sidebarMoreBtn');
    var menu = document.getElementById('sidebarMoreMenu');
    var moreWrap = trigger ? trigger.closest('.sidebar-more') : null;
    var list = document.querySelector('.sidebar-navlist');

    // ---------- open/close ----------
    if (trigger && menu) {
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
    }

    // ---------- adaptive overflow ----------
    // Least-essential-first: the order icons drop out of the bar and into "More" as space runs
    // out. Home never drops (it's the way out of the immersive feed) and Shorts's own self-link
    // is already permanently hidden here by CSS, so neither is in this list.
    var DROP_ORDER = ['/drafts', '/bookmarks', '/following', '/follow', '/notifications'];

    function adjust() {
        if (!list || !moreWrap) return;

        // Reset to the "everything fits" baseline before re-measuring - otherwise a bar that
        // narrowed then widened again would stay stuck with icons hidden from the last pass.
        // More shows FIRST, during measurement - its own width needs to already be claimed
        // before checking whether the rest fits, or an item that "fit" a moment ago could
        // overflow the instant More reserves its own space right after. The menu's own copy of
        // each item resets alongside the bar's - it should only ever list what's NOT already
        // visible directly, never both at once.
        DROP_ORDER.forEach(function (href) {
            var item = list.querySelector('.sidebar-navitem[href="' + href + '"]');
            if (item) item.style.display = '';
            var menuItem = menu ? menu.querySelector('.nav-dd-item[href="' + href + '"]') : null;
            if (menuItem) menuItem.hidden = true;
        });
        moreWrap.style.display = 'flex';

        var hiddenAny = false;
        for (var i = 0; i < DROP_ORDER.length && list.scrollWidth > list.clientWidth; i++) {
            var href = DROP_ORDER[i];
            var el = list.querySelector('.sidebar-navitem[href="' + href + '"]');
            if (!el) continue;
            el.style.display = 'none';
            hiddenAny = true;
            var menuItem = menu ? menu.querySelector('.nav-dd-item[href="' + href + '"]') : null;
            if (menuItem) menuItem.hidden = false;
        }
        moreWrap.style.display = hiddenAny ? 'flex' : 'none';
    }

    if (list && moreWrap) {
        adjust();
        // Debounced - resize (including a phone's own orientation change) can fire many times
        // in quick succession; re-measuring on every single one is wasted work for a layout that
        // only actually needs the final settled width.
        var resizeTimer = null;
        window.addEventListener('resize', function () {
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(adjust, 120);
        });
    }
});

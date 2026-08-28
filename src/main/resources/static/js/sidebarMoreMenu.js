// Two jobs for every page's mobile top bar (fragments/sidebar.html) - included on every page
// that renders the shared sidebar, so the same collapsing behavior is consistent everywhere the
// nav squeezes down, not a Shorts-only patch. A complete no-op on desktop, where dashboardStyle.css
// keeps .sidebar-more's own base rule at display:none and .sidebar-navlist never overflows.
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
        // The dropdown's own default positioning (.nav-profile-dropdown: position:absolute;
        // right:0, anchored to .sidebar-more's own relative box) works for the profile menu
        // because that trigger sits at the true right edge of the bar - but "More" sits mid-bar,
        // well short of the edge, so the same anchoring ran the panel off the LEFT side of the
        // screen (confirmed by measuring it: left ended up negative). A single static CSS fix
        // can't cover every page, either - Shorts' own top bar is position:fixed while every
        // other page's collapses in-flow (position:relative, scrolls away), so a hardcoded "top"
        // offset that happens to line up with one page's header height silently drifts wrong on
        // any other. Computing the trigger's real on-screen position at the moment it's opened,
        // then clamping into the viewport, is correct regardless of which layout the page uses -
        // the trigger can only be tapped while it's actually visible, so its rect at open time is
        // always where the menu needs to appear.
        function position() {
            var r = trigger.getBoundingClientRect();
            var margin = 8;
            menu.style.position = 'fixed';
            var width = menu.offsetWidth || 220;
            var left = Math.min(r.right - width, window.innerWidth - width - margin);
            left = Math.max(left, margin);
            menu.style.left = left + 'px';
            menu.style.right = 'auto';
            menu.style.top = (r.bottom + margin) + 'px';
        }

        function close() {
            menu.hidden = true;
            trigger.setAttribute('aria-expanded', 'false');
        }

        function toggle(e) {
            e.stopPropagation();
            var next = menu.hidden;
            if (next) {
                // Unhide first - offsetWidth (used to clamp the left edge) reads as 0 on a
                // still-hidden element, so position() needs the panel already visible to measure.
                menu.hidden = false;
                position();
            } else {
                menu.hidden = true;
            }
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
    // out. Home never drops - it's the way out of the immersive feed, and the only item every
    // page (including Shorts, which hides its own self-link entirely via CSS) always keeps.
    // /shorts IS in this list, unlike originally - leaving it un-droppable meant it sat pinned
    // next to Home on every OTHER page regardless of width, so once everything else had already
    // collapsed into "More" and Home+Shorts alone still didn't fit, .sidebar-navlist's own
    // overflow-x:auto had nothing left to hide and fell back to a visible native scrollbar - a
    // real bug found by actually narrowing the window rather than assuming the drop list covered
    // every icon that could ever need to move.
    var DROP_ORDER = ['/shorts', '/drafts', '/bookmarks', '/following', '/follow', '/notifications'];

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
            // Skip revealing the menu counterpart for whichever page you're actually ON (e.g.
            // Shorts's own entry, on a page other than Shorts, is fine to offer - but Shorts's
            // entry while you're already viewing Shorts, or Notifications's while already on
            // Notifications, would just be "go to the page you're already on", which is exactly
            // the kind of redundant-entry-in-the-menu bug already found once with Notifications
            // duplicating between the bar and the dropdown. .active is set server-side
            // (fragments/sidebar.html's th:classappend) regardless of whether CSS also hides the
            // element outright (Shorts's own self-link), so this check covers both cases.
            if (el.classList.contains('active')) continue;
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
            // An open menu's position was computed for the old width - rather than recompute
            // mid-resize, just close it; whichever icons now fit are a click away again.
            if (menu && !menu.hidden) menu.hidden = true;
            clearTimeout(resizeTimer);
            resizeTimer = setTimeout(adjust, 120);
        });
    }
});

// Locks page scroll while any modal/lightbox overlay is open, on both the register page
// (avatar + cover pickers) and the profile page (avatar + cover editors, avatar lightbox).
// Without this, the underlying page can still scroll behind a fixed-position overlay taller
// than the viewport, showing a page-level scrollbar that looks like it belongs to the modal.
// Centralized here (rather than in every open/close handler) so a new overlay just needs the
// same "hidden" attribute toggle to get this for free.
(function () {
    var OVERLAY_SELECTOR = '.avatar-modal-backdrop, .modal-backdrop, .lightbox, .follow-list-backdrop';

    function anyOverlayOpen() {
        var overlays = document.querySelectorAll(OVERLAY_SELECTOR);
        for (var i = 0; i < overlays.length; i++) {
            if (!overlays[i].hidden) return true;
        }
        return false;
    }

    function sync() {
        document.body.style.overflow = anyOverlayOpen() ? 'hidden' : '';
    }

    document.addEventListener('DOMContentLoaded', function () {
        var overlays = document.querySelectorAll(OVERLAY_SELECTOR);
        if (!overlays.length) return;

        var observer = new MutationObserver(sync);
        overlays.forEach(function (el) {
            observer.observe(el, { attributes: true, attributeFilter: ['hidden'] });
        });
        sync();
    });
})();

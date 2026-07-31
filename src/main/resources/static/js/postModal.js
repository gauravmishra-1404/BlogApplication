// Opens a post in a modal over the dashboard feed instead of navigating to /post/viewPost -
// the feed's scroll position and everything infiniteScroll.js has already loaded stay exactly
// as they were, restored the instant the modal closes since none of it ever unmounted.
//
// Comment-add, reply, edit, and delete forms inside the modal are intercepted and submitted
// via fetch, then the whole modal fragment is re-fetched to show the result - simplest way to
// keep the thread correct (nested replies, reaction summaries, edited/deleted state) without
// hand-rolling DOM removal/insertion for every case commentNode.html already handles
// server-side. Comment-edit and comment-delete's own controllers both redirect to
// /post/viewPost?id=X - fine for the standalone page, but left un-intercepted here that would
// silently navigate the whole browser away from the modal to that old template, exactly the
// "different template depending on entry point" inconsistency the rest of this modal exists to
// avoid.
//
// Comment-delete is confirm-gated (fragments/confirmDialog.html + js/confirmDialog.js), which
// fully owns the form's raw 'submit' event until the user actually decides - so this listens for
// confirmDialog.js's own 'confirmed-submit' event instead, and only for delete forms; add/reply/
// edit aren't confirm-gated, so they're intercepted on the raw 'submit' event as before. Post
// edit/delete are NOT intercepted here - post edit already opens the compose modal directly (see
// share.js), and post delete's own redirect target (back to wherever it was opened from, or
// /home) is already the right place to land, so there's nothing left to keep in-place.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('postModalBackdrop');
    var frame = document.getElementById('postModalFrame');
    if (!backdrop || !frame) return;

    var currentPostId = null;

    function isCommentSubmitForm(form) {
        var action = form.getAttribute('action') || '';
        return action.indexOf('/comments/add') !== -1 ||
            action.indexOf('/comments/edit') !== -1 ||
            /\/reply(\?.*)?$/.test(action);
    }

    function isCommentDeleteForm(form) {
        var action = form.getAttribute('action') || '';
        return action.indexOf('/comments/delete') !== -1;
    }

    function interceptAndReload(form) {
        fetch(form.getAttribute('action'), { method: 'POST', body: new FormData(form) })
            .then(function () {
                if (currentPostId) loadInto(currentPostId);
            });
    }

    function open(postId) {
        currentPostId = postId;
        frame.innerHTML = '';
        backdrop.hidden = false;
        document.body.style.overflow = 'hidden';
        loadInto(postId);
    }

    function close() {
        backdrop.hidden = true;
        frame.innerHTML = '';
        document.body.style.overflow = '';
        currentPostId = null;
    }

    function loadInto(postId) {
        fetch('/post/' + postId + '/modal', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) { return response.text(); })
            .then(function (html) {
                frame.innerHTML = html;
                // .options-menu's URL input has no server-side value, only ever set by
                // share.js's own DOMContentLoaded pass - which never runs for content injected
                // after the page has already loaded. Same gap infiniteScroll.js already handles
                // for appended feed rows.
                window.BodhSeaShare.populateUrls(frame);
                window.BodhSeaShare.initDownloadConfirms(frame);
            })
            .catch(function () {
                // Fetch itself failed (network) - fall back to the real page rather than leave
                // an empty modal open.
                window.location.href = '/post/viewPost?id=' + postId;
            });
    }

    document.addEventListener('click', function (event) {
        // A dedicated attribute rather than reusing .post-row's own data-post-id: the already-
        // open post-view modal's root (fragments/postModal.html) also carries data-post-id (its
        // own, unrelated purpose - see js/share.js's edit handler), and that root is an ancestor
        // of everything inside the open modal - a bare [data-post-id] selector here would match
        // it too and incorrectly re-open on any click inside the modal that isn't otherwise
        // handled. Used by both the dashboard feed (fragments/postRows.html) and profile.html's
        // Posts/Replies tabs, wherever "click this to open the post-view modal" applies.
        var row = event.target.closest('[data-open-post-modal]');
        if (row) {
            event.preventDefault();
            open(row.dataset.openPostModal);
            return;
        }

        if (event.target.closest('[data-modal-close]')) {
            close();
            return;
        }

        if (event.target === backdrop) {
            close();
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && !backdrop.hidden) close();
    });

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.closest('.post-modal') || !isCommentSubmitForm(form)) return;

        event.preventDefault();
        var submitBtn = form.querySelector('.composer-submit');
        if (submitBtn) submitBtn.disabled = true;

        fetch(form.getAttribute('action'), { method: 'POST', body: new FormData(form) })
            .then(function () {
                if (currentPostId) loadInto(currentPostId);
            })
            .catch(function () {
                if (submitBtn) submitBtn.disabled = false;
            });
    });

    // Fires only once confirmDialog.js's dialog has actually been accepted (see that file) -
    // never on the raw, unconfirmed 'submit'.
    document.addEventListener('confirmed-submit', function (event) {
        var form = event.target;
        if (!form.closest('.post-modal') || !isCommentDeleteForm(form)) return;

        event.preventDefault();
        interceptAndReload(form);
    });
});

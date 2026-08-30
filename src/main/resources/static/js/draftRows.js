// Drafts page (draftsPage.html) - clicking a row opens it in the same compose modal already
// used for Create/Edit (js/composeModal.js's openForEdit()), reading the draft's current data
// straight out of the row's own data attributes rather than a second fetch, same approach
// js/share.js's Edit button already uses for a published post.
document.addEventListener('DOMContentLoaded', function () {
    // Real bug fixed here: this used to bail out on `!rows.length` alone, which also skipped
    // the Short-tile wiring further below whenever there were zero Post drafts (Posts 0 /
    // Shorts >0 - exactly the case where the tabs default to the Shorts tab) - a Short draft
    // tile rendered fine but its click/keydown listeners were never attached at all. The modal's
    // own presence is the only thing both blocks actually depend on; each collection's own
    // emptiness only skips that collection's own wiring, not the other one's.
    if (!window.BodhSeaCompose) return;

    var rows = document.querySelectorAll('.draft-row');

    function openRow(row) {
        var tags = (row.dataset.postTags || '').split(',').map(function (t) { return t.trim(); }).filter(Boolean);
        var media = [];
        if (row.dataset.postMedia) {
            try { media = JSON.parse(row.dataset.postMedia); } catch (e) { media = []; }
        }
        window.BodhSeaCompose.openForEdit({
            id: row.dataset.postId,
            title: row.dataset.postTitle || '',
            content: row.dataset.postContent || '',
            tags: tags,
            media: media,
            isDraft: true
        });
    }

    rows.forEach(function (row) {
        row.addEventListener('click', function () { openRow(row); });
        row.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                openRow(row);
            }
        });
    });

    // Short drafts - same page, same modal, just a tile instead of a row and a different data
    // shape (draftsPage.html's shorts-tile-grid section). No length guard needed here - forEach
    // on an empty NodeList is already a no-op, same as the Post rows above.
    var shortTiles = document.querySelectorAll('.shorts-tile[data-short-id]');

    function openShortTile(tile) {
        window.BodhSeaCompose.openForEdit({
            id: tile.dataset.shortId,
            type: 'short',
            caption: tile.dataset.caption || '',
            videoUrl: tile.dataset.videoUrl || '',
            scheduledAt: tile.dataset.scheduledAt || '',
            isDraft: true
        });
    }

    shortTiles.forEach(function (tile) {
        tile.addEventListener('click', function () { openShortTile(tile); });
        tile.addEventListener('keydown', function (event) {
            if (event.key === 'Enter' || event.key === ' ') {
                event.preventDefault();
                openShortTile(tile);
            }
        });
    });

    // ---------- delete (Post draft rows + Short draft tiles) ----------
    // Both forms POST to the same already-ownership-checked endpoints a published Post/Short's
    // own Delete button already uses (PostController.deletePost / ShortsController.deleteShort -
    // PostAuthorization.isOwnerOrAdmin) - no new backend authorization to get right, just a new
    // way to reach the same checked action. fragments/confirmDialog.html + js/confirmDialog.js
    // already own the "are you sure?" step generically; this only owns what happens once the
    // user actually confirms - submitting via fetch (so the row disappears in place, no full
    // page reload) instead of confirmDialog.js's own default real form.submit().
    var deleteForms = document.querySelectorAll('.draft-delete-form, .shorts-tile-delete-form');

    deleteForms.forEach(function (form) {
        // Stops the click from also bubbling up to the row's/tile's own listener above (which
        // would otherwise open the compose modal at the same time the delete confirms) - a real
        // interaction bug checked for while building this, not a hypothetical one.
        form.addEventListener('click', function (event) { event.stopPropagation(); });

        form.addEventListener('confirmed-submit', function (event) {
            event.preventDefault();
            var container = form.closest('.draft-row, .shorts-tile');
            var button = form.querySelector('button');
            if (button) button.disabled = true;

            fetch(form.action, { method: 'POST', body: new FormData(form) })
                .then(function (response) {
                    if (!response.ok) throw new Error('delete failed with status ' + response.status);
                    if (container) {
                        // Fade out in place rather than yank it away instantly - matches the
                        // same "let the user see what just happened" reasoning .bookmark-toggle's
                        // own pulse animation already uses elsewhere.
                        container.style.transition = 'opacity 0.2s ease';
                        container.style.opacity = '0';
                        setTimeout(function () { container.remove(); }, 200);
                    }
                    // The tab's own count badge was rendered from the page-load count - without
                    // this it would keep showing one more than the list actually holds now.
                    var tabName = form.classList.contains('shorts-tile-delete-form') ? 'shorts' : 'posts';
                    var countEl = document.querySelector('.tab-item[data-tab="' + tabName + '"] .tab-count');
                    if (countEl) countEl.textContent = Math.max(0, (parseInt(countEl.textContent, 10) || 0) - 1);
                    if (window.showToast) window.showToast('Draft deleted');
                })
                .catch(function (error) {
                    console.error('Draft delete failed:', error);
                    if (window.showToast) window.showToast('Could not delete draft - try again.', true);
                    if (button) button.disabled = false;
                });
        });
    });
});

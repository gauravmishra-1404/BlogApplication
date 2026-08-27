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
});

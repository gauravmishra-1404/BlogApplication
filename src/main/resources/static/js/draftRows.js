// Drafts page (draftsPage.html) - clicking a row opens it in the same compose modal already
// used for Create/Edit (js/composeModal.js's openForEdit()), reading the draft's current data
// straight out of the row's own data attributes rather than a second fetch, same approach
// js/share.js's Edit button already uses for a published post.
document.addEventListener('DOMContentLoaded', function () {
    var rows = document.querySelectorAll('.draft-row');
    if (!rows.length || !window.BodhSeaCompose) return;

    function openRow(row) {
        var tags = (row.dataset.postTags || '').split(',').map(function (t) { return t.trim(); }).filter(Boolean);
        window.BodhSeaCompose.openForEdit({
            id: row.dataset.postId,
            title: row.dataset.postTitle || '',
            content: row.dataset.postContent || '',
            tags: tags,
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
});

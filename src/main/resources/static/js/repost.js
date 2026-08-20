// Repost toggling - same shape as js/bookmark.js (event delegation, stopImmediatePropagation so
// a click inside the modal's reaction bar doesn't also trigger postModal.js's open-on-click, POST
// to a toggle endpoint, update every matching element on the page). Only ever shows up on the
// full post page / modal reaction bar (viewPostByID.html, fragments/postModal.html) - the
// compact feed-row count in postRows.html is a read-only display, not a button, so there's
// nothing to wire a click handler to there.
document.addEventListener('click', function (event) {
    var button = event.target.closest('.repost-toggle');
    if (!button || !button.dataset.postId) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    if (button.dataset.loggedIn !== 'true') {
        window.location.href = '/login';
        return;
    }

    var postId = button.dataset.postId;
    button.disabled = true;
    fetch('/api/posts/' + postId + '/repost', { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Repost toggle failed with status ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            // Every instance of this same post's toggle on the page (the modal AND the full
            // page, if somehow both are present) reflects the same state, not just the one
            // clicked - same reasoning js/bookmark.js's own toggle already follows.
            document.querySelectorAll('.repost-toggle[data-post-id="' + postId + '"]').forEach(function (el) {
                el.classList.toggle('active', summary.reposted);
                var label = el.querySelector('.repost-toggle-label');
                if (label) {
                    label.textContent = summary.reposted ? 'Reposted' : 'Repost';
                }
                var count = el.querySelector('.reaction-count');
                if (count) {
                    count.textContent = summary.repostCount;
                }
            });
        })
        .catch(function (error) {
            // Best-effort UI enhancement - a network hiccup shouldn't break the page, the
            // button just stays at its last known state until the next successful click.
            console.error('Repost toggle failed:', error);
        })
        .finally(function () {
            button.disabled = false;
        });
});

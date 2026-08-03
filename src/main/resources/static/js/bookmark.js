// Bookmark toggling for every place a .bookmark-toggle shows up: the dashboard/Following/
// Bookmarks feed rows (fragments/postRows.html, nested inside the whole-row .post-row <a> - a
// real <button> there is safe, same conclusion already verified for .follow-row's own button),
// and the full post page / modal's reaction bar (viewPostByID.html, fragments/postModal.html).
// event.stopImmediatePropagation() matches js/follow.js's own reasoning: needed for the
// feed-row case so the row's own <a> navigation and postModal.js's open-on-click listener don't
// also fire, harmless on the standalone reaction-bar button.
document.addEventListener('click', function (event) {
    var button = event.target.closest('.bookmark-toggle');
    if (!button || !button.dataset.postId) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    if (button.dataset.loggedIn !== 'true') {
        window.location.href = '/login';
        return;
    }

    var postId = button.dataset.postId;
    button.disabled = true;
    fetch('/api/posts/' + postId + '/bookmark', { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Bookmark toggle failed with status ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            // Every instance of this same post's toggle on the page (a feed row AND the modal
            // opened from it, for example) reflects the same state, not just the one clicked.
            document.querySelectorAll('.bookmark-toggle[data-post-id="' + postId + '"]').forEach(function (el) {
                el.classList.toggle('saved', summary.bookmarked);
                el.classList.remove('pulse');
                void el.offsetWidth; // restart the pulse animation even on rapid re-toggles
                el.classList.add('pulse');
                el.setAttribute('aria-label', summary.bookmarked ? 'Remove bookmark' : 'Save for later');
                var label = el.querySelector('.bookmark-pill-label');
                if (label) {
                    label.textContent = summary.bookmarked ? 'Saved' : 'Save';
                }
            });
        })
        .catch(function (error) {
            // Best-effort UI enhancement - a network hiccup shouldn't break the page, the
            // button just stays at its last known state until the next successful click.
            console.error('Bookmark toggle failed:', error);
        })
        .finally(function () {
            button.disabled = false;
        });
});

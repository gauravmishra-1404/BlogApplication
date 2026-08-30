// Bookmark toggling for every place a .bookmark-toggle shows up: the dashboard/Following/
// Bookmarks feed rows (fragments/postRows.html, nested inside the whole-row .post-row <a> - a
// real <button> there is safe, same conclusion already verified for .follow-row's own button),
// the full post page / modal's reaction bar (viewPostByID.html, fragments/postModal.html), and
// now the Shorts feed/modal (fragments/shortsCard.html, fragments/shortModal.html).
//
// Generic over data-target-type ("post" or "short") + data-target-id, same pattern
// js/reactions.js already established - the URL is built by pluralizing targetType, so adding
// Shorts here needed no new file, just this one change (was hardcoded to data-post-id and
// /api/posts/.../bookmark before this feature).
//
// event.stopImmediatePropagation() matches js/follow.js's own reasoning: needed for the
// feed-row case so the row's own <a> navigation and postModal.js's open-on-click listener don't
// also fire, harmless on the standalone reaction-bar button.
document.addEventListener('click', function (event) {
    var button = event.target.closest('.bookmark-toggle');
    if (!button || !button.dataset.targetType || !button.dataset.targetId) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    if (button.dataset.loggedIn !== 'true') {
        window.location.href = '/login';
        return;
    }

    var targetType = button.dataset.targetType;
    var targetId = button.dataset.targetId;
    var url = '/api/' + targetType + 's/' + targetId + '/bookmark';
    button.disabled = true;
    fetch(url, { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Bookmark toggle failed with status ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            // Every instance of this same item's toggle on the page (a feed row AND the modal
            // opened from it, for example) reflects the same state, not just the one clicked.
            document.querySelectorAll('.bookmark-toggle[data-target-type="' + targetType + '"][data-target-id="' + targetId + '"]').forEach(function (el) {
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

            // bookmarksPage.html specifically (not the Home/Following feed, nor a post's own
            // page, where the same toggle correctly just flips state in place): un-bookmarking
            // here means the item no longer belongs on a page whose entire point is "things I
            // bookmarked" - it should leave the list, not just show as unsaved. Scoped to these
            // two containers' ids (both only ever rendered on this one page) rather than every
            // .bookmark-toggle everywhere, so nothing else changes behavior.
            if (!summary.bookmarked) {
                var scoped = button.closest('#bookmarksFeedList, #bookmarksShortsGrid');
                var item = scoped ? button.closest('.post-row, .shorts-tile') : null;
                if (item) {
                    item.style.transition = 'opacity 0.2s ease';
                    item.style.opacity = '0';
                    setTimeout(function () { item.remove(); }, 200);
                    if (window.showToast) window.showToast('Removed from bookmarks');
                    var tabName = targetType === 'short' ? 'shorts' : 'posts';
                    var countEl = document.querySelector('.tab-item[data-tab="' + tabName + '"] .tab-count');
                    if (countEl) countEl.textContent = Math.max(0, (parseInt(countEl.textContent, 10) || 0) - 1);
                }
            }
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

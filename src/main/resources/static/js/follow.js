// Follow/unfollow toggling for every place a .follow-btn shows up: the profile page's own
// header (a standalone button, #profileFollowBtn), the dashboard's "Active writers" widget (a
// button nested inside the whole-row .follow-row <a>), and each row of the followers/following
// modal (js/followListModal.js, also nested inside a whole-row <a>). event.stopImmediatePropagation()
// is required for both nested cases - stopPropagation() alone doesn't stop sibling document-level
// click listeners registered by other scripts (postModal.js/profileLinks.js), only
// stopImmediatePropagation() does; harmless on the standalone header button, so one handler
// covers all three locations.
document.addEventListener('click', function (event) {
    var button = event.target.closest('.follow-btn');
    // The "You" badge on your own row in a followers/following list shares this class for
    // matching visual treatment but has no data-username (it's not a real toggle) - bail out
    // rather than firing a fetch to '/api/users/undefined/follow'.
    if (!button || !button.dataset.username) return;

    event.preventDefault();
    event.stopImmediatePropagation();

    if (button.dataset.loggedIn !== 'true') {
        window.location.href = '/login';
        return;
    }

    var username = button.dataset.username;
    button.disabled = true;
    fetch('/api/users/' + username + '/follow', { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Follow toggle failed with status ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            button.classList.toggle('following', summary.following);
            button.textContent = summary.following ? 'Following' : 'Follow';

            // Only the profile page's own header button changes the currently-displayed
            // profile's follower count - a dashboard-widget or modal-row click toggles the
            // viewer's relationship with some OTHER user, never with the page's own profileUser.
            if (button.id === 'profileFollowBtn') {
                var followerCountEl = document.getElementById('followerCountValue');
                if (followerCountEl) {
                    followerCountEl.textContent = summary.followerCount;
                }
            }

            // js/followListModal.js listens for this to handle its own list-specific effects
            // (removing a row, adjusting the Following tab count) - harmless no-op everywhere
            // else since nothing else listens for it.
            button.dispatchEvent(new CustomEvent('follow-toggled', {
                bubbles: true,
                detail: { following: summary.following, followerCount: summary.followerCount, username: username }
            }));
        })
        .catch(function (error) {
            console.error('Follow toggle failed:', error);
        })
        .finally(function () {
            button.disabled = false;
        });
});

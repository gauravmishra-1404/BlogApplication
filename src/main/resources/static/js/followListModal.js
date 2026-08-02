// Followers/Following list modal (fragments/followListModal.html), opened by clicking either
// stat on a profile page. One shell, two tabs - each tab switch re-fetches that tab's rows
// fresh from the server (simplest correct approach; no client-side caching to keep in sync).
// js/follow.js drives the actual follow/unfollow toggle for each row's button (event delegation
// on document means newly-injected rows work with zero extra wiring here) and fires a
// 'follow-toggled' custom event this file listens for to handle list-specific side effects.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('followListBackdrop');
    if (!backdrop) return;

    var body = document.getElementById('followListBody');
    var followersTabBtn = document.getElementById('followersTabBtn');
    var followingTabBtn = document.getElementById('followingTabBtn');
    var closeBtn = document.getElementById('followListClose');

    var profileUsername = backdrop.dataset.profileUsername;
    var isOwnProfile = backdrop.dataset.isOwnProfile === 'true';
    var activeTab = null;

    function setActiveTab(tab) {
        activeTab = tab;
        backdrop.dataset.activeTab = tab;
        followersTabBtn.classList.toggle('active', tab === 'followers');
        followingTabBtn.classList.toggle('active', tab === 'following');
    }

    function loadTab(tab) {
        setActiveTab(tab);
        body.innerHTML = '';
        fetch('/profile/' + profileUsername + '/' + tab, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) { return response.text(); })
            .then(function (html) {
                body.innerHTML = html;
            });
    }

    function open(tab) {
        backdrop.hidden = false;
        loadTab(tab);
    }

    function close() {
        backdrop.hidden = true;
        body.innerHTML = '';
    }

    document.addEventListener('click', function (event) {
        var trigger = event.target.closest('[data-open-follow-list]');
        if (trigger) {
            open(trigger.dataset.openFollowList);
            return;
        }

        if (event.target.closest('#followListClose')) {
            close();
            return;
        }

        if (event.target === backdrop) {
            close();
            return;
        }

        var tabBtn = event.target.closest('.follow-list-tab');
        if (tabBtn && !backdrop.hidden) {
            loadTab(tabBtn.dataset.followTab);
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key === 'Escape' && !backdrop.hidden) close();
    });

    // A row's own toggle only ever changes the VIEWER's relationship with that row's user, never
    // with the profile whose modal is open (that profile can't appear in its own list) - so this
    // never needs to touch followerCountValue. It only needs to react when the viewer is looking
    // at their OWN Following tab: unfollowing there means that person no longer belongs in this
    // exact list, so the row comes out and the Following tab/page counts follow along. Every
    // other case (Followers tab, or someone else's Following list) - the row stays, only its own
    // button changed, which js/follow.js already handled.
    document.addEventListener('follow-toggled', function (event) {
        var row = event.target.closest('.user-row');
        if (!row || backdrop.hidden) return;

        if (isOwnProfile) {
            var followingCountValue = document.getElementById('followingCountValue');
            if (followingCountValue) {
                var delta = event.detail.following ? 1 : -1;
                followingCountValue.textContent = Math.max(0, parseInt(followingCountValue.textContent, 10) + delta);
            }
        }

        if (isOwnProfile && activeTab === 'following' && !event.detail.following) {
            row.remove();
            var followingTabCount = document.getElementById('followingTabCount');
            if (followingTabCount) {
                followingTabCount.textContent = Math.max(0, parseInt(followingTabCount.textContent, 10) - 1);
            }
            if (!body.querySelector('.user-row')) {
                loadTab('following'); // re-fetch to show the empty state cleanly
            }
        }
    });
});

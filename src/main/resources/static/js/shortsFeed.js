// The immersive vertical-swipe Shorts feed (templates/shortsPage.html) - four independent
// concerns live in this one file since they're all specific to this one page:
//
// 1. Autoplay/pause via IntersectionObserver - exactly one .shorts-card-video plays at a time
//    (whichever card is mostly in view), everything else pauses. Native browser scroll-snap
//    (.shorts-feed's own CSS) drives the actual paging - no gesture library needed.
// 1b. Live URL tracking - as the active card changes, the address bar quietly updates to
//    /shorts/{that card's id} (history.replaceState, no reload) - same behavior YouTube's own
//    Shorts player has: the URL always reflects whichever video is currently centered, not just
//    the one you originally opened, so grabbing the URL mid-scroll and sharing it points at the
//    right one. replaceState, not pushState - scrolling through a whole session of Shorts must
//    not turn the browser's Back button into "step back one video at a time."
// 2. Infinite scroll, same near-bottom/rAF-throttle shape js/infiniteScroll.js already
//    established for the dashboard, scoped to #shortsFeed and GET /shorts/fragment instead.
// 3. The comment-thread overlay (js/postModal.js's own fetch-and-inject pattern, against
//    /short/{id}/modal and fragments/shortModal.html instead of the Post equivalents), plus a
//    plain Share button (Web Share API where available, clipboard-copy + toast otherwise - see
//    fragments/shortsCard.html's own comment on why this isn't fragments/shareMenu.html).
document.addEventListener('DOMContentLoaded', function () {
    var feed = document.getElementById('shortsFeed');
    if (!feed) return;

    // ---------- 1 & 1b. autoplay/pause + live URL tracking ----------
    // video.dataset.userPaused tracks a deliberate tap-to-pause (see the tap handler below) so
    // the observer doesn't immediately override it - it only resumes autoplay on the NEXT time
    // the card actually re-enters view (scrolled away and back), same as every other short-video
    // app: a manual pause holds while you're looking at it, but doesn't follow the video forever.
    var activeShortId = null;
    var observer = new IntersectionObserver(function (entries) {
        entries.forEach(function (entry) {
            var video = entry.target;
            if (entry.isIntersecting && entry.intersectionRatio >= 0.6) {
                if (video.dataset.userPaused !== 'true') {
                    video.play().catch(function () { /* autoplay can be blocked, tap-to-pause below still works */ });
                }

                var card = video.closest('.shorts-card');
                var shortId = card && card.dataset.shortId;
                if (shortId && shortId !== activeShortId) {
                    activeShortId = shortId;
                    history.replaceState(null, '', '/shorts/' + shortId);
                }
            } else {
                video.pause();
                video.dataset.userPaused = '';
            }
        });
    }, { threshold: [0, 0.6, 1] });

    function observeVideos(root) {
        root.querySelectorAll('.shorts-card-video').forEach(function (video) {
            observer.observe(video);
        });
    }
    observeVideos(feed);

    // Center-screen play/pause flash - the only feedback for the tap below, fades on its own.
    function flashPauseIcon(video, iconId) {
        var card = video.closest('.shorts-card');
        var flash = card && card.querySelector('.shorts-pause-flash');
        if (!flash) return;
        var use = flash.querySelector('use');
        if (use) use.setAttribute('href', '#' + iconId);
        flash.classList.remove('show');
        void flash.offsetWidth; // restart the fade-out animation even on rapid re-taps
        flash.classList.add('show');
    }

    // Tap a card's video to pause/resume - mute is its own separate button (below), not bundled
    // into this same tap, since sound is a deliberate choice a viewer makes independently of
    // whether they want the video playing at all.
    feed.addEventListener('click', function (event) {
        var muteBtn = event.target.closest('.shorts-mute-btn');
        if (muteBtn) {
            var muteCard = muteBtn.closest('.shorts-card');
            var muteVideo = muteCard && muteCard.querySelector('.shorts-card-video');
            if (!muteVideo) return;
            muteVideo.muted = !muteVideo.muted;
            var muteIcon = muteBtn.querySelector('use');
            if (muteIcon) muteIcon.setAttribute('href', muteVideo.muted ? '#i-volume-mute' : '#i-volume');
            muteBtn.setAttribute('aria-label', muteVideo.muted ? 'Unmute' : 'Mute');
            return;
        }

        var video = event.target.closest('.shorts-card-video');
        if (!video) return;
        if (video.paused) {
            video.play().catch(function () { /* autoplay can still be blocked even on a direct tap in rare cases */ });
            video.dataset.userPaused = '';
            flashPauseIcon(video, 'i-play');
        } else {
            video.pause();
            video.dataset.userPaused = 'true';
            flashPauseIcon(video, 'i-pause');
        }
    });

    // ---------- 1c. playback progress bar ----------
    // 'timeupdate' doesn't bubble, but the capture phase still reaches every descendant
    // regardless - one delegated listener here instead of wiring each video individually as
    // cards get appended by infinite scroll below. Naturally holds in place when paused (no
    // more timeupdate events fire) and wraps to 0 on loop, since that's just currentTime's own
    // native behavior - no separate reset logic needed for either case.
    feed.addEventListener('timeupdate', function (event) {
        var video = event.target;
        if (!video.classList || !video.classList.contains('shorts-card-video')) return;
        if (!isFinite(video.duration) || video.duration <= 0) return;
        var card = video.closest('.shorts-card');
        var fill = card && card.querySelector('.shorts-progress-fill');
        if (fill) fill.style.width = (video.currentTime / video.duration) * 100 + '%';
    }, true);

    // ---------- 2. infinite scroll ----------
    var status = document.getElementById('shortsScrollStatus');
    var nextPage = parseInt(feed.dataset.nextPage, 10);
    var isLoading = false;
    var ticking = false;

    var initialWrapper = feed.querySelector('[data-has-next-page]');
    var hasMore = initialWrapper ? initialWrapper.dataset.hasNextPage === 'true' : false;

    function nearBottom() {
        var threshold = 600;
        return window.innerHeight + window.scrollY >= document.body.offsetHeight - threshold;
    }

    function loadNextBatch() {
        isLoading = true;
        if (status) status.hidden = false;

        fetch('/shorts/fragment?page=' + nextPage, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) {
                if (!response.ok) throw new Error('Fragment fetch failed: ' + response.status);
                return response.text();
            })
            .then(function (html) {
                var temp = document.createElement('div');
                temp.innerHTML = html;
                var wrapper = temp.firstElementChild;
                hasMore = wrapper ? wrapper.dataset.hasNextPage === 'true' : false;

                feed.insertAdjacentHTML('beforeend', html);
                observeVideos(feed);

                nextPage += 1;
                isLoading = false;
                if (status) status.hidden = true;
            })
            .catch(function () {
                isLoading = false;
                if (status) status.hidden = true;
            });
    }

    window.addEventListener('scroll', function () {
        if (ticking) return;
        ticking = true;
        requestAnimationFrame(function () {
            ticking = false;
            if (!hasMore || isLoading) return;
            if (nearBottom()) loadNextBatch();
        });
    }, { passive: true });

    // ---------- 3. comment-thread overlay ----------
    var modalBackdrop = document.getElementById('shortModalBackdrop');
    var modalFrame = document.getElementById('shortModalFrame');
    var currentShortId = null;

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

    function loadModal(shortId) {
        fetch('/short/' + shortId + '/modal', { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) { return response.text(); })
            .then(function (html) {
                modalFrame.innerHTML = html;
            })
            .catch(function () {
                modalFrame.innerHTML = '';
            });
    }

    function openModal(shortId) {
        if (!modalBackdrop || !modalFrame) return;
        currentShortId = shortId;
        modalFrame.innerHTML = '';
        modalBackdrop.hidden = false;
        document.body.style.overflow = 'hidden';
        loadModal(shortId);
    }

    function closeModal() {
        if (!modalBackdrop || !modalFrame) return;
        modalBackdrop.hidden = true;
        modalFrame.innerHTML = '';
        document.body.style.overflow = '';
        currentShortId = null;
    }

    document.addEventListener('click', function (event) {
        var opener = event.target.closest('[data-open-short-modal]');
        if (opener) {
            openModal(opener.dataset.openShortModal);
            return;
        }

        if (event.target.closest('[data-modal-close]')) {
            closeModal();
            return;
        }

        if (event.target === modalBackdrop) {
            closeModal();
            return;
        }

        var shareBtn = event.target.closest('[data-share-short]');
        if (shareBtn) {
            var card = shareBtn.closest('.shorts-card');
            var shortId = card ? card.dataset.shortId : null;
            // A real per-Short URL now (GET /shorts/{id}) - not a #fragment on the plain list,
            // which never actually pinned anything when reloaded.
            var url = window.location.origin + '/shorts' + (shortId ? '/' + shortId : '');
            if (navigator.share) {
                navigator.share({ url: url }).catch(function () { /* user cancelled - nothing to do */ });
            } else if (navigator.clipboard) {
                navigator.clipboard.writeText(url).then(function () {
                    if (window.showToast) window.showToast('Link copied');
                });
            }
            return;
        }

        // Edit only ever renders (owner/admin) inside fragments/shortModal.html - same
        // "read straight out of the already-open modal's own DOM, no second fetch" pattern
        // js/share.js's Post-edit handler already uses, applied to that fragment's own root
        // data attributes instead of scraping child text/tag chips (a Short has no tags/gallery).
        var editBtn = event.target.closest('[data-edit-short]');
        if (editBtn) {
            if (!window.BodhSeaCompose) return;
            var shortModal = editBtn.closest('.short-modal');
            if (!shortModal) return;
            closeModal();
            window.BodhSeaCompose.openForEdit({
                id: shortModal.dataset.shortId,
                type: 'short',
                caption: shortModal.dataset.caption || '',
                videoUrl: shortModal.dataset.videoUrl || '',
                scheduledAt: shortModal.dataset.scheduledAt || '',
                isDraft: shortModal.dataset.isDraft === 'true'
            });
        }
    });

    if (modalBackdrop) {
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && !modalBackdrop.hidden) closeModal();
        });

        document.addEventListener('submit', function (event) {
            var form = event.target;
            if (!form.closest('.short-modal') || !isCommentSubmitForm(form)) return;

            event.preventDefault();
            var submitBtn = form.querySelector('.composer-submit');
            if (submitBtn) submitBtn.disabled = true;

            fetch(form.getAttribute('action'), { method: 'POST', body: new FormData(form) })
                .then(function () {
                    if (currentShortId) loadModal(currentShortId);
                })
                .catch(function () {
                    if (submitBtn) submitBtn.disabled = false;
                });
        });

        document.addEventListener('confirmed-submit', function (event) {
            var form = event.target;
            if (!form.closest('.short-modal') || !isCommentDeleteForm(form)) return;

            event.preventDefault();
            fetch(form.getAttribute('action'), { method: 'POST', body: new FormData(form) })
                .then(function () {
                    if (currentShortId) loadModal(currentShortId);
                });
        });
    }
});

// Replaces the old page-by-page pagination on the dashboard (postDashboard.html) with
// scroll-triggered loading. Fetches fragments/postRows.html batches from
// GET /home/fragment (PostController.postsFragment), reusing whatever filter/sort/query is
// already active (read off #postList's own data attributes, set server-side from the same
// activeQuery/activeAuthors/activeTags/activeOrder the initial page rendered with).
document.addEventListener('DOMContentLoaded', function () {
    var list = document.getElementById('postList');
    var status = document.getElementById('infiniteScrollStatus');
    if (!list || !status) return;

    var nextPage = parseInt(list.dataset.nextPage, 10);
    var pageSize = list.dataset.pageSize;
    var query = list.dataset.query || '';
    var authors = list.dataset.authors ? list.dataset.authors.split('|') : [];
    var tags = list.dataset.tags ? list.dataset.tags.split('|') : [];
    var order = list.dataset.order || '';

    // The initial render's own postRows wrapper already carries data-has-next-page - read it
    // instead of duplicating that decision as a separate model attribute just for the JS.
    var initialWrapper = list.querySelector('[data-has-next-page]');
    var hasMore = initialWrapper ? initialWrapper.dataset.hasNextPage === 'true' : false;
    var isLoading = false;
    // Throttled via rAF, not a raw scroll listener - scroll fires far more often than the page
    // actually needs to check anything, and the isLoading flag alone doesn't stop the check
    // itself from running on every single scroll event.
    var ticking = false;

    function buildFragmentUrl() {
        var params = new URLSearchParams();
        params.set('page', String(nextPage));
        if (pageSize) params.set('size', pageSize);
        if (query) params.set('query', query);
        authors.forEach(function (a) { if (a) params.append('author', a); });
        tags.forEach(function (t) { if (t) params.append('tag', t); });
        if (order) params.set('order', order);
        return '/home/fragment?' + params.toString();
    }

    function nearBottom() {
        var threshold = 600; // start loading a bit before the user actually hits the very end
        return window.innerHeight + window.scrollY >= document.body.offsetHeight - threshold;
    }

    function loadNextBatch() {
        isLoading = true;
        status.hidden = false;

        fetch(buildFragmentUrl(), { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
            .then(function (response) {
                if (!response.ok) throw new Error('Fragment fetch failed: ' + response.status);
                return response.text();
            })
            .then(function (html) {
                var temp = document.createElement('div');
                temp.innerHTML = html;
                var wrapper = temp.firstElementChild;
                hasMore = wrapper ? wrapper.dataset.hasNextPage === 'true' : false;

                list.insertAdjacentHTML('beforeend', html);
                window.BodhSeaShare.populateUrls(list);
                window.BodhSeaShare.initDownloadConfirms(list);

                nextPage += 1;
                isLoading = false;
                status.hidden = true;
            })
            .catch(function () {
                // Leave hasMore as-is so the next scroll simply retries, rather than
                // permanently giving up over what might be a transient failure.
                isLoading = false;
                status.hidden = true;
            });
    }

    function onScroll() {
        if (ticking) return;
        ticking = true;
        requestAnimationFrame(function () {
            ticking = false;
            if (!hasMore || isLoading) return;
            if (nearBottom()) loadNextBatch();
        });
    }

    window.addEventListener('scroll', onScroll, { passive: true });
});

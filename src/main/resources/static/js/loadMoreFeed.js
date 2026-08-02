// Generic "Load more" pagination, shared by every page that paginates this way (the "Follow"
// directory and the "Following" feed so far) - container/button ids and the fragment endpoint
// come from data attributes on the list element rather than being hardcoded per page, so a new
// page reuses this unchanged instead of copying a near-identical script (the same duplication
// risk that split the sidebar markup apart before fragments/sidebar.html was extracted).
// Usage: <div data-load-more-list data-load-more-button="btnId" data-load-more-url="/x/fragment"
//             data-next-page="1">...initial rows, ending in a [data-has-next-page] wrapper...</div>
document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('[data-load-more-list]').forEach(function (list) {
        var btn = document.getElementById(list.dataset.loadMoreButton);
        if (!btn) return;

        var nextPage = parseInt(list.dataset.nextPage, 10);
        var endpoint = list.dataset.loadMoreUrl;

        function syncVisibility() {
            // Each loaded batch appends its own [data-has-next-page] wrapper, so after 2+ pages
            // there are multiple matches in the DOM - only the LAST one (the most recently
            // loaded page) reflects current reality; querySelector's first-match would keep
            // reading page 1's now-stale value forever.
            var wrappers = list.querySelectorAll('[data-has-next-page]');
            var latest = wrappers[wrappers.length - 1];
            var hasMore = latest ? latest.dataset.hasNextPage === 'true' : false;
            btn.hidden = !hasMore;
        }

        btn.addEventListener('click', function () {
            btn.disabled = true;
            fetch(endpoint + '?page=' + nextPage, { headers: { 'X-Requested-With': 'XMLHttpRequest' } })
                .then(function (response) {
                    if (!response.ok) throw new Error('Fragment fetch failed: ' + response.status);
                    return response.text();
                })
                .then(function (html) {
                    list.insertAdjacentHTML('beforeend', html);
                    nextPage += 1;
                    syncVisibility();
                })
                .catch(function (error) {
                    console.error('Load more failed:', error);
                })
                .finally(function () {
                    btn.disabled = false;
                });
        });

        syncVisibility();
    });
});

// Like/dislike toggling for posts and comments (fragments/commentNode.html and
// viewPostByID.html). Each reaction bar carries data-target-type ("post"/"comment"),
// data-target-id, and data-logged-in; each button inside it carries data-reaction
// ("LIKE"/"DISLIKE"). Clicking calls the toggle API and updates just this bar in place -
// no page reload, matching how liking/disliking feels on YouTube or Twitter.
document.addEventListener('click', function (event) {
    var button = event.target.closest('.reaction-btn');
    if (!button) return;

    var bar = button.closest('.reaction-bar');
    if (!bar) return;

    if (bar.dataset.loggedIn !== 'true') {
        window.location.href = '/login';
        return;
    }

    var targetType = bar.dataset.targetType; // "post" or "comment"
    var targetId = bar.dataset.targetId;
    var reaction = button.dataset.reaction; // "LIKE" or "DISLIKE"
    var url = '/api/' + targetType + 's/' + targetId + '/reactions/' + reaction.toLowerCase();

    button.disabled = true;
    fetch(url, { method: 'POST', headers: { 'Accept': 'application/json' } })
        .then(function (response) {
            if (!response.ok) {
                throw new Error('Reaction request failed with status ' + response.status);
            }
            return response.json();
        })
        .then(function (summary) {
            applySummary(bar, summary);
        })
        .catch(function (error) {
            // Best-effort UI enhancement - a network hiccup shouldn't break the page, the
            // button just stays at its last known state until the next successful click.
            console.error('Reaction toggle failed:', error);
        })
        .finally(function () {
            button.disabled = false;
        });
});

function applySummary(bar, summary) {
    var likeButton = bar.querySelector('.reaction-btn[data-reaction="LIKE"]');
    var dislikeButton = bar.querySelector('.reaction-btn[data-reaction="DISLIKE"]');

    setCount(likeButton, summary.likeCount);
    setCount(dislikeButton, summary.dislikeCount);

    likeButton.classList.toggle('active', summary.userReaction === 'LIKE');
    dislikeButton.classList.toggle('active', summary.userReaction === 'DISLIKE');
}

function setCount(button, value) {
    var countEl = button.querySelector('.reaction-count');
    if (countEl) {
        countEl.textContent = formatCompact(value);
    }
}

// Same compact K/M/B/T notation as the server renders initially (NumberFormatter.compact in
// Java) - using the browser's own Intl.NumberFormat rather than hand-rolled thresholds here
// too, so a live update after a click never looks inconsistent with the page's first render.
function formatCompact(value) {
    return new Intl.NumberFormat('en-US', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

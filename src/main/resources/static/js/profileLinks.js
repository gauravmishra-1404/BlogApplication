// Avatar/author links inside a dashboard post-row (postDashboard.html). The row itself is one
// big <a>, and nesting a real <a> (or <button>) inside it would trigger the same HTML5
// parsing bug already fixed for the share menu - the browser auto-closes and reopens the
// outer <a>, silently splitting the card in two. So this is a plain <span role="link"> instead.
//
// preventDefault() (not just stopPropagation()) is required to stop the row's own <a> from
// navigating - stopPropagation only blocks ancestor *listeners*, it does nothing to an
// anchor's native "follow this link" behavior. And this listener is registered on the
// *capture* phase (the trailing `true`) specifically so it runs before the click ever reaches
// the target span - a bubble-phase listener here would itself get skipped by the very
// stopPropagation() this handler needs to call.
document.addEventListener('click', function (event) {
    var link = event.target.closest('.post-author-link');
    if (!link) return;
    event.preventDefault();
    event.stopPropagation();
    window.location.href = link.dataset.profileUrl;
}, true);

document.addEventListener('keydown', function (event) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    var link = event.target.closest('.post-author-link');
    if (!link) return;
    event.preventDefault();
    event.stopPropagation();
    window.location.href = link.dataset.profileUrl;
}, true);

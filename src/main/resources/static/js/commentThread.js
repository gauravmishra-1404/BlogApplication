// Toggles a comment's reply box open/closed, and a comment's nested replies
// collapsed/expanded (fragments/commentNode.html). "Reply" carries a data-toggle-reply
// attribute naming its box's id; the "N replies" button carries data-toggle-replies -
// nested threads start collapsed so a long comment section doesn't dump every reply
// on-screen at once.
document.addEventListener('click', function (event) {
    var replyToggle = event.target.closest('[data-toggle-reply]');
    if (replyToggle) {
        var box = document.getElementById(replyToggle.getAttribute('data-toggle-reply'));
        if (box) {
            box.classList.toggle('open');
        }
        return;
    }

    var repliesToggle = event.target.closest('[data-toggle-replies]');
    if (repliesToggle) {
        var replies = document.getElementById(repliesToggle.getAttribute('data-toggle-replies'));
        if (replies) {
            var expanded = repliesToggle.getAttribute('aria-expanded') === 'true';
            repliesToggle.setAttribute('aria-expanded', String(!expanded));
            replies.hidden = expanded;
        }
    }
});

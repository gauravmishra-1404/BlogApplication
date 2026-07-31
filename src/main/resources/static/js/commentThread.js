// Toggles a comment's reply box open/closed, and a comment's nested replies
// collapsed/expanded (fragments/commentNode.html). "Reply" carries a data-toggle-reply
// attribute naming its box's id; the "N replies" button carries data-toggle-replies -
// nested threads start collapsed so a long comment section doesn't dump every reply
// on-screen at once. "Edit" (data-toggle-edit) swaps the comment's text for an inline
// textarea the same way, rather than navigating to a separate edit page.
document.addEventListener('click', function (event) {
    var replyToggle = event.target.closest('[data-toggle-reply]');
    if (replyToggle) {
        var box = document.getElementById(replyToggle.getAttribute('data-toggle-reply'));
        if (box) {
            box.classList.toggle('open');
        }
        return;
    }

    var editToggle = event.target.closest('[data-toggle-edit]');
    if (editToggle) {
        var editBox = document.getElementById(editToggle.getAttribute('data-toggle-edit'));
        if (editBox) {
            var opening = !editBox.classList.contains('open');
            // Reset any unsaved typing left over from a previously abandoned edit (closed via
            // this same toggle rather than Cancel) so reopening always starts from the real
            // current comment text.
            if (opening) {
                var editTa = editBox.querySelector('textarea');
                if (editTa) editTa.value = editTa.defaultValue;
            }
            editBox.classList.toggle('open', opening);
        }
        return;
    }

    // .defaultValue reflects the textarea's originally-rendered content regardless of
    // whatever the user has since typed into .value - resetting to it on Cancel needs no
    // separate "what was this before" bookkeeping.
    var editCancel = event.target.closest('[data-cancel-edit]');
    if (editCancel) {
        var cancelBox = document.getElementById(editCancel.getAttribute('data-cancel-edit'));
        if (cancelBox) {
            var ta = cancelBox.querySelector('textarea');
            if (ta) ta.value = ta.defaultValue;
            cancelBox.classList.remove('open');
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

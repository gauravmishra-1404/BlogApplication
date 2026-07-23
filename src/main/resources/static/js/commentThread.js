// Toggles a comment's reply box open/closed (fragments/commentNode.html).
// Each "Reply" button carries a data-toggle-reply attribute naming its box's id.
document.addEventListener('click', function (event) {
    var toggle = event.target.closest('[data-toggle-reply]');
    if (!toggle) return;
    var box = document.getElementById(toggle.getAttribute('data-toggle-reply'));
    if (box) {
        box.classList.toggle('open');
    }
});

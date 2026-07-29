// Posts/Replies tab switching on the profile page (profile.html) - plain show/hide, no routing.
document.addEventListener('click', function (event) {
    var tab = event.target.closest('.tab-item');
    if (!tab) return;

    var bar = tab.parentElement;
    bar.querySelectorAll('.tab-item').forEach(function (t) {
        t.classList.toggle('active', t === tab);
    });

    var card = bar.closest('.profile-card');
    card.querySelectorAll('.tab-panel').forEach(function (panel) {
        panel.hidden = panel.dataset.panel !== tab.dataset.tab;
    });
});

// Generic .tab-bar/.tab-item/.tab-panel switching - plain show/hide, no routing. Started as
// profile.html's Posts/Replies tabs; draftsPage.html's Posts/Shorts tabs reuse the exact same
// markup+CSS, so this scopes off .tab-bar's own parent (whatever wraps a bar and its panels as
// siblings) rather than a profile-specific container, so one script drives every .tab-bar on the
// site instead of each page needing its own copy.
document.addEventListener('click', function (event) {
    var tab = event.target.closest('.tab-item');
    if (!tab) return;

    var bar = tab.parentElement;
    bar.querySelectorAll('.tab-item').forEach(function (t) {
        t.classList.toggle('active', t === tab);
    });

    var scope = bar.parentElement;
    scope.querySelectorAll('.tab-panel').forEach(function (panel) {
        panel.hidden = panel.dataset.panel !== tab.dataset.tab;
    });
});

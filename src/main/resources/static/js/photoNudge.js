// Reveals the "Add a profile photo" banner on the owner's own profile page (server already
// decided whether it's eligible - see profile.html's th:if - this only handles the dismiss
// state, which is purely a client-side preference, not worth a DB column or a request just to
// remember "I closed this once"). Starts `hidden` in the markup so a dismissed user never sees
// even a flash of it before this runs.
document.addEventListener('DOMContentLoaded', function () {
    var nudge = document.getElementById('photoNudge');
    if (!nudge) return;

    var dismissKey = nudge.dataset.dismissKey;
    if (dismissKey && localStorage.getItem(dismissKey) === '1') return;

    nudge.hidden = false;

    var openBtn = document.getElementById('photoNudgeOpen');
    var dismissBtn = document.getElementById('photoNudgeDismiss');
    var avatarModal = document.getElementById('avatarModalBackdrop');

    if (openBtn && avatarModal) {
        openBtn.addEventListener('click', function () {
            avatarModal.hidden = false;
        });
    }
    if (dismissBtn) {
        dismissBtn.addEventListener('click', function () {
            if (dismissKey) localStorage.setItem(dismissKey, '1');
            nudge.hidden = true;
        });
    }
});

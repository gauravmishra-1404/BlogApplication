// Owns the "Edit profile" dropdown (Profile photo / Cover photo / disabled Personal info &
// Settings) and the avatar zoom lightbox on profile.html. Deliberately separate from
// avatarEditor.js/coverEditor.js, which only handle what happens once their modal is already
// open - this file is just the entry points into each.
document.addEventListener('DOMContentLoaded', function () {
    var editBtn = document.getElementById('editProfileBtn');
    var dropdown = document.getElementById('editDropdown');

    if (editBtn && dropdown) {
        editBtn.addEventListener('click', function (e) {
            e.stopPropagation();
            var isOpen = dropdown.classList.toggle('open');
            editBtn.classList.toggle('open', isOpen);
        });
        document.addEventListener('click', function () {
            dropdown.classList.remove('open');
            editBtn.classList.remove('open');
        });
        dropdown.querySelectorAll('button[data-action]').forEach(function (btn) {
            btn.addEventListener('click', function (e) {
                e.stopPropagation();
                dropdown.classList.remove('open');
                editBtn.classList.remove('open');
                var action = btn.dataset.action;
                var target = action === 'avatar' ? document.getElementById('avatarModalBackdrop')
                        : action === 'cover' ? document.getElementById('coverModalBackdrop')
                        : null;
                if (target) target.hidden = false;
            });
        });
    }

    // ---- Avatar zoom (view-only, no editing controls) ----
    var avatarTrigger = document.getElementById('profileAvatarZoom');
    var lightbox = document.getElementById('avatarLightbox');
    var lightboxClose = document.getElementById('lightboxClose');
    if (avatarTrigger && lightbox) {
        avatarTrigger.addEventListener('click', function () { lightbox.hidden = false; });
        lightboxClose.addEventListener('click', function () { lightbox.hidden = true; });
        lightbox.addEventListener('click', function (e) {
            if (e.target === lightbox) lightbox.hidden = true;
        });
    }
});

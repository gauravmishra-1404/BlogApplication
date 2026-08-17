// Drives the "Customize your avatar" modal on profile.html: tab switching, live preview, and
// keeping the 4 hidden inputs (mode/preset/swatchIndex/hue) in sync with whichever option the
// user last touched, so a plain multipart POST to /profile/avatar carries only what that mode
// needs - the server (ProfileController + AvatarPresets) re-derives every gradient itself.
// Opening the modal itself is profileEditMenu.js's job (the "Edit profile" dropdown) - this
// file only owns what happens once it's open.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('avatarModalBackdrop');
    if (!backdrop) return;

    var initial = backdrop.dataset.initial || '?';
    var preview = document.getElementById('avatarPreview');
    var closeBtn = document.getElementById('avatarModalClose');
    var cancelBtn = document.getElementById('avatarCancelBtn');

    var modeInput = document.getElementById('avatarMode');
    var presetInput = document.getElementById('avatarPresetInput');
    var swatchInput = document.getElementById('avatarSwatchInput');
    var hueInput = document.getElementById('avatarHueInput');
    var photoUrlInput = document.getElementById('avatarPhotoUrlInput');
    var uploadStatus = document.getElementById('avatarUploadStatus');
    var saveBtn = backdrop.querySelector('.btn-save');

    closeBtn.addEventListener('click', function () { backdrop.hidden = true; });
    cancelBtn.addEventListener('click', function () { backdrop.hidden = true; });
    backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) backdrop.hidden = true;
    });

    // ---- Tabs ----
    var tabs = backdrop.querySelectorAll('.avatar-tab');
    var panels = backdrop.querySelectorAll('.avatar-panel');
    tabs.forEach(function (tab) {
        tab.addEventListener('click', function () {
            tabs.forEach(function (t) { t.classList.remove('active'); });
            panels.forEach(function (p) { p.classList.remove('active'); });
            tab.classList.add('active');
            backdrop.querySelector('.avatar-panel[data-panel="' + tab.dataset.panel + '"]').classList.add('active');
        });
    });

    function setPreview(innerHtml, background) {
        preview.style.background = background;
        preview.innerHTML = innerHtml;
    }

    // ---- Photo ----
    // Local preview is instant (FileReader, unchanged); the real upload happens in parallel via
    // js/profileImageUpload.js - straight to S3, this server never sees the bytes. Submit stays
    // disabled only while that upload is actually in flight; ProfileController.updateAvatar
    // already treats mode=photo with an empty photoUrl as a safe no-op, so there's no need to be
    // more defensive than that if someone submits right as an upload fails.
    var fileInput = document.getElementById('avatarFileInput');
    fileInput.addEventListener('change', function (e) {
        var file = e.target.files[0];
        if (!file) return;
        modeInput.value = 'photo';
        photoUrlInput.value = '';

        var reader = new FileReader();
        reader.onload = function (ev) {
            setPreview('<img src="' + ev.target.result + '" alt="">', 'none');
        };
        reader.readAsDataURL(file);

        window.profileImageUpload.upload(file, 'avatar', {
            onStart: function () {
                saveBtn.disabled = true;
                uploadStatus.hidden = false;
                uploadStatus.textContent = 'Uploading...';
                uploadStatus.classList.remove('dz-status-error');
            },
            onProgress: function (pct) {
                uploadStatus.textContent = 'Uploading... ' + pct + '%';
            },
            onSuccess: function (publicUrl) {
                photoUrlInput.value = publicUrl;
                saveBtn.disabled = false;
                uploadStatus.hidden = true;
            },
            onError: function (message) {
                saveBtn.disabled = false;
                uploadStatus.hidden = false;
                uploadStatus.textContent = message;
                uploadStatus.classList.add('dz-status-error');
            }
        });
    });

    // ---- Presets ----
    backdrop.querySelectorAll('.avatar-preset-option').forEach(function (btn) {
        btn.addEventListener('click', function () {
            backdrop.querySelectorAll('.avatar-preset-option').forEach(function (b) { b.classList.remove('selected'); });
            btn.classList.add('selected');
            modeInput.value = 'preset';
            presetInput.value = btn.dataset.key;
            setPreview(
                '<svg class="icon avatar-icon" aria-hidden="true"><use href="#i-av-' + btn.dataset.key + '"></use></svg>',
                btn.dataset.gradient
            );
        });
    });

    // ---- Curated color swatches ----
    backdrop.querySelectorAll('.avatar-color-option').forEach(function (btn) {
        btn.addEventListener('click', function () {
            backdrop.querySelectorAll('.avatar-color-option').forEach(function (b) { b.classList.remove('selected'); });
            btn.classList.add('selected');
            modeInput.value = 'color';
            swatchInput.value = btn.dataset.index;
            hueInput.value = '';
            setPreview('<span>' + initial + '</span>', btn.dataset.gradient);
        });
    });

    // ---- Custom hue mix ----
    document.getElementById('avatarHueSlider').addEventListener('input', function (e) {
        var h = parseInt(e.target.value, 10);
        var h2 = (h + 45) % 360;
        var gradient = 'linear-gradient(135deg, hsl(' + h + ',75%,62%) 0%, hsl(' + h2 + ',70%,48%) 100%)';
        backdrop.querySelectorAll('.avatar-color-option').forEach(function (b) { b.classList.remove('selected'); });
        modeInput.value = 'color';
        swatchInput.value = '';
        hueInput.value = h;
        setPreview('<span>' + initial + '</span>', gradient);
    });
});

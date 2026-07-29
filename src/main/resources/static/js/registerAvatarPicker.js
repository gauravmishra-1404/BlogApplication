// Drives the "Customize your avatar" modal on the register page. Unlike the profile page's
// version, there's no account yet to POST to - every selection just updates 4 hidden inputs
// (avatarMode/avatarPreset/avatarSwatchIndex/avatarHue) plus the visible avatar-input file, and
// rides along with the rest of the registration form on submit. UserServiceImpl.applyAvatar
// resolves the same way ProfileController.updateAvatar does.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('avatar-circle');
    var backdrop = document.getElementById('avatarModalBackdrop');
    if (!trigger || !backdrop) return;

    var modalPreview = document.getElementById('avatarModalPreview');
    var outerCircle = document.getElementById('avatar-circle');
    var closeBtn = document.getElementById('avatarModalClose');
    var doneBtn = document.getElementById('avatarModalDone');
    var nameInput = document.getElementById('name');

    var modeInput = document.getElementById('avatarMode');
    var presetInput = document.getElementById('avatarPresetInput');
    var swatchInput = document.getElementById('avatarSwatchInput');
    var hueInput = document.getElementById('avatarHueInput');
    var identityHint = document.getElementById('identityHint');

    if (nameInput && identityHint) {
        nameInput.addEventListener('input', function () {
            var name = nameInput.value.trim();
            identityHint.textContent = name ? name : 'This is how your profile will look';
        });
    }

    trigger.addEventListener('click', function () { backdrop.hidden = false; });
    closeBtn.addEventListener('click', function () { backdrop.hidden = true; });
    doneBtn.addEventListener('click', function () { backdrop.hidden = true; });
    backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) backdrop.hidden = true;
    });

    // No "?" placeholder here - at signup someone may well open the color picker before typing
    // their name, and a plain colored circle reads as intentional where "?" reads as an error.
    function currentInitial() {
        var name = nameInput ? nameInput.value.trim() : '';
        return name ? name.charAt(0).toUpperCase() : '';
    }

    // The outer circle keeps its "cam-badge" corner icon regardless of what's selected; only
    // the modal's own preview is a plain circle (no badge) matching the profile page's editor.
    function setPreview(innerHtml, background) {
        modalPreview.style.background = background;
        modalPreview.innerHTML = innerHtml;

        outerCircle.classList.add('filled');
        outerCircle.style.background = background;
        var badge = outerCircle.querySelector('.cam-badge');
        outerCircle.innerHTML = innerHtml;
        if (badge) outerCircle.appendChild(badge);
    }

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

    // ---- Photo ----
    document.getElementById('avatarFileInput').addEventListener('change', function (e) {
        var file = e.target.files[0];
        if (!file) return;
        modeInput.value = 'photo';
        var reader = new FileReader();
        reader.onload = function (ev) {
            setPreview('<img src="' + ev.target.result + '" alt="">', 'none');
        };
        reader.readAsDataURL(file);
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
            setPreview('<span>' + currentInitial() + '</span>', btn.dataset.gradient);
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
        setPreview('<span>' + currentInitial() + '</span>', gradient);
    });
});

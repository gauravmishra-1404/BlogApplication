// Drives the "Cover photo" modal on the register page - mirrors registerAvatarPicker.js exactly
// (tabs, live preview, keeping the 4 hidden inputs in sync) but for the wide banner instead of
// the circular avatar. The first scene is pre-selected in the markup itself (coverPresetInput's
// th:value + coverMode defaulting to "preset"), so an untouched signup still submits a valid
// cover rather than none at all.
document.addEventListener('DOMContentLoaded', function () {
    var trigger = document.getElementById('identityBanner');
    var backdrop = document.getElementById('coverModalBackdrop');
    if (!trigger || !backdrop) return;

    var modalPreview = document.getElementById('coverModalPreview');
    var identityBanner = document.getElementById('identityBanner');

    var closeBtn = document.getElementById('coverModalClose');
    var doneBtn = document.getElementById('coverModalDone');

    var modeInput = document.getElementById('coverMode');
    var presetInput = document.getElementById('coverPresetInput');
    var swatchInput = document.getElementById('coverSwatchInput');
    var hueInput = document.getElementById('coverHueInput');

    var editPillHtml = '<span class="identity-edit-pill">' +
        '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2Z"/><circle cx="12" cy="13" r="4"/></svg>Cover</span>';

    function openModal(e) { e.preventDefault(); backdrop.hidden = false; }
    trigger.addEventListener('click', openModal);
    trigger.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ' ') openModal(e);
    });
    closeBtn.addEventListener('click', function () { backdrop.hidden = true; });
    doneBtn.addEventListener('click', function () { backdrop.hidden = true; });
    backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) backdrop.hidden = true;
    });

    // Every call fully rebuilds both previews from scratch (rather than mutating a persistent
    // <svg>/<img> child), so photo/preset/color modes never have to worry about which element
    // a previous mode left behind.
    function setPreview(innerHtml, background) {
        modalPreview.style.background = background;
        modalPreview.innerHTML = innerHtml;
        identityBanner.style.background = background;
        identityBanner.innerHTML = innerHtml + editPillHtml;
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
    document.getElementById('coverFileInput').addEventListener('change', function (e) {
        var file = e.target.files[0];
        if (!file) return;
        modeInput.value = 'photo';
        var reader = new FileReader();
        reader.onload = function (ev) {
            setPreview('<img src="' + ev.target.result + '" alt="">', 'none');
        };
        reader.readAsDataURL(file);
    });

    // ---- Scene presets ----
    backdrop.querySelectorAll('.cover-preset-option').forEach(function (btn) {
        btn.addEventListener('click', function () {
            backdrop.querySelectorAll('.cover-preset-option').forEach(function (b) { b.classList.remove('selected'); });
            btn.classList.add('selected');
            modeInput.value = 'preset';
            presetInput.value = btn.dataset.key;
            setPreview(
                '<svg viewBox="0 0 400 140" preserveAspectRatio="xMidYMid slice"><use href="#cover-' + btn.dataset.key + '"></use></svg>',
                'none'
            );
        });
    });

    // ---- Curated color swatches ----
    backdrop.querySelectorAll('.cover-color-option').forEach(function (btn) {
        btn.addEventListener('click', function () {
            backdrop.querySelectorAll('.cover-color-option').forEach(function (b) { b.classList.remove('selected'); });
            btn.classList.add('selected');
            modeInput.value = 'color';
            swatchInput.value = btn.dataset.index;
            hueInput.value = '';
            setPreview('', btn.dataset.gradient);
        });
    });

    // ---- Custom hue mix ----
    document.getElementById('coverHueSlider').addEventListener('input', function (e) {
        var h = parseInt(e.target.value, 10);
        var h2 = (h + 45) % 360;
        var gradient = 'linear-gradient(135deg, hsl(' + h + ',75%,62%) 0%, hsl(' + h2 + ',70%,48%) 100%)';
        backdrop.querySelectorAll('.cover-color-option').forEach(function (b) { b.classList.remove('selected'); });
        modeInput.value = 'color';
        swatchInput.value = '';
        hueInput.value = h;
        setPreview('', gradient);
    });
});

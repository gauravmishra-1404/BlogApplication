// Drives the "Cover photo" modal on profile.html - mirrors avatarEditor.js exactly (tabs, live
// preview, 4 hidden inputs kept in sync with whichever option was last touched) but for the
// wide banner instead of the circular avatar, and with no fallback "initial" content for color
// mode - an empty gradient rectangle is a complete cover image on its own.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('coverModalBackdrop');
    if (!backdrop) return;

    var preview = document.getElementById('coverPreview');
    var closeBtn = document.getElementById('coverModalClose');
    var cancelBtn = document.getElementById('coverCancelBtn');

    var modeInput = document.getElementById('coverMode');
    var presetInput = document.getElementById('coverPresetInput');
    var swatchInput = document.getElementById('coverSwatchInput');
    var hueInput = document.getElementById('coverHueInput');

    closeBtn.addEventListener('click', function () { backdrop.hidden = true; });
    cancelBtn.addEventListener('click', function () { backdrop.hidden = true; });
    backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) backdrop.hidden = true;
    });

    // ---- Tabs ----
    var tabs = backdrop.querySelectorAll('.cover-tab');
    var panels = backdrop.querySelectorAll('.cover-panel');
    tabs.forEach(function (tab) {
        tab.addEventListener('click', function () {
            tabs.forEach(function (t) { t.classList.remove('active'); });
            panels.forEach(function (p) { p.classList.remove('active'); });
            tab.classList.add('active');
            backdrop.querySelector('.cover-panel[data-panel="' + tab.dataset.panel + '"]').classList.add('active');
        });
    });

    function setPreview(innerHtml, background) {
        preview.style.background = background;
        preview.innerHTML = innerHtml;
    }

    // ---- Photo ----
    var fileInput = document.getElementById('coverFileInput');
    fileInput.addEventListener('change', function (e) {
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

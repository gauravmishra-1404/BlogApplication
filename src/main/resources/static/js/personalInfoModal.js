// Drives the "Personal info" modal on profile.html - a row list (Username / Email / Mobile),
// each opened into its own single-field panel rather than one shared form (see the modal's own
// doc comment in profile.html for why). Panel switching, the password re-auth gate ahead of
// Email/Mobile, live username-availability checking, and OTP-digit entry are all client-side;
// the actual mutations are plain form POSTs that reload the page (same convention
// avatarEditor.js/coverEditor.js already use), not fetch/AJAX.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('personalInfoModalBackdrop');
    if (!backdrop) return;

    var closeBtn = document.getElementById('pinfoModalClose');
    var panels = backdrop.querySelectorAll('.pinfo-panel');

    function showPanel(name) {
        panels.forEach(function (p) { p.classList.toggle('active', p.dataset.pinfoPanel === name); });
    }

    function resetAndClose() {
        backdrop.hidden = true;
        showPanel('list');
    }

    closeBtn.addEventListener('click', resetAndClose);
    backdrop.addEventListener('click', function (e) {
        if (e.target === backdrop) resetAndClose();
    });

    // Clicking "Personal info" always starts at the row list - the auto-open case right below
    // handles the "a code is already waiting" state, so this click path never needs to guess.
    document.querySelectorAll('[data-action="personal-info"]').forEach(function (trigger) {
        trigger.addEventListener('click', function () {
            showPanel('list');
        });
    });

    // request-otp's own form POST is a full page reload (same plain-form convention
    // avatarEditor.js/coverEditor.js already use, not fetch/AJAX) - landing back on the profile
    // page with nothing but a toast and a closed modal left no obvious way back to where the
    // code actually goes. Auto-open straight into the confirm panel instead of waiting for the
    // user to rediscover "Edit profile > Personal info" on their own.
    if (backdrop.dataset.mobileOtpPending === 'true') {
        backdrop.hidden = false;
        showPanel('confirm-otp');
    }

    // ---- Row / back navigation ----
    var pendingNext = null;
    backdrop.querySelectorAll('[data-pinfo-goto]').forEach(function (el) {
        el.addEventListener('click', function () {
            if (el.dataset.pinfoNext) pendingNext = el.dataset.pinfoNext;
            showPanel(el.dataset.pinfoGoto);
        });
    });

    // ---- Password re-auth gate (Email/Mobile only - Username needs none) ----
    var reauthPassword = document.getElementById('pinfoReauthPassword');
    var reauthError = document.getElementById('pinfoReauthError');
    document.getElementById('pinfoReauthContinue').addEventListener('click', function () {
        var value = reauthPassword.value;
        if (!value) {
            reauthError.hidden = false;
            return;
        }
        reauthError.hidden = true;
        var carryId = pendingNext === 'edit-email' ? 'pinfoEmailPasswordCarry' : 'pinfoMobilePasswordCarry';
        document.getElementById(carryId).value = value;
        reauthPassword.value = '';
        showPanel(pendingNext);
    });

    // ---- Live username availability ----
    var usernameInput = document.getElementById('pinfoUsernameInput');
    var usernameHint = document.getElementById('pinfoUsernameHint');
    var usernameSave = document.getElementById('pinfoUsernameSave');
    var originalUsername = usernameInput.value;
    var debounceTimer = null;

    usernameInput.addEventListener('input', function () {
        var value = usernameInput.value.trim().toLowerCase();
        clearTimeout(debounceTimer);

        if (value === originalUsername) {
            usernameHint.textContent = '3-30 characters: letters, numbers, and underscores.';
            usernameHint.className = 'pinfo-hint';
            usernameSave.disabled = false;
            return;
        }
        if (!/^[a-z0-9_]{3,30}$/.test(value)) {
            usernameHint.textContent = 'Letters, numbers, and underscores only (3-30 characters).';
            usernameHint.className = 'pinfo-hint bad';
            usernameSave.disabled = true;
            return;
        }

        debounceTimer = setTimeout(function () {
            fetch('/api/users/check-username?username=' + encodeURIComponent(value), { headers: { 'Accept': 'application/json' } })
                .then(function (r) { return r.json(); })
                .then(function (result) {
                    if (usernameInput.value.trim().toLowerCase() !== value) return; // stale response
                    usernameHint.textContent = result.available ? '✓ Available' : 'That username is already taken.';
                    usernameHint.className = 'pinfo-hint ' + (result.available ? 'ok' : 'bad');
                    usernameSave.disabled = !result.available;
                })
                .catch(function () { /* best-effort - server still validates on submit */ });
        }, 400);
    });

    // ---- OTP digit boxes -> single hidden field before submit ----
    var otpForm = document.getElementById('pinfoOtpForm');
    if (otpForm) {
        var digits = Array.prototype.slice.call(backdrop.querySelectorAll('.pinfo-otp-digit'));
        var otpHidden = document.getElementById('pinfoOtpCode');

        digits.forEach(function (digit, index) {
            digit.addEventListener('input', function () {
                digit.value = digit.value.replace(/[^0-9]/g, '').slice(0, 1);
                if (digit.value && index < digits.length - 1) digits[index + 1].focus();
            });
            digit.addEventListener('keydown', function (e) {
                if (e.key === 'Backspace' && !digit.value && index > 0) digits[index - 1].focus();
            });
        });

        otpForm.addEventListener('submit', function () {
            otpHidden.value = digits.map(function (d) { return d.value; }).join('');
        });
    }
});

// Client-side-only password strength hint (LinkedIn-style: a quiet one-line rule reminder plus
// a slim 4-segment bar), matching the composition rule most large platforms settled on: 8+
// characters and at least 3 of the 4 character classes (lower/upper/digit/special), rejected
// outright if it's one of the passwords everyone tries first. This is deliberately NOT enforced
// server-side - it's a UX guardrail, not a security boundary; the account is only as strong as
// whatever a direct API call chooses to send.
(function () {
    // A short, well-known set of the passwords attackers try first - not an exhaustive breach
    // list (that would need a server round-trip), just enough to catch the obvious cases a
    // pure composition check would otherwise happily call "strong" (e.g. "Password1!").
    var COMMON_PASSWORDS = new Set([
        'password', 'password1', 'password123', 'passw0rd', 'p@ssw0rd', 'p@ssword',
        '12345678', '123456789', '1234567890', 'qwerty123', 'qwertyuiop',
        'letmein', 'welcome', 'welcome1', 'admin123', 'iloveyou',
        'monkey123', 'football', 'dragon123', 'master123', 'sunshine',
        'princess', 'trustno1', 'abc123456', 'shadow123', 'superman1',
        'starwars1', 'freedom1', 'whatever', 'passw0rd1', 'baseball1',
        'hunter123', 'access123', 'letmein1', 'login123', 'changeme'
    ]);

    var COLORS = ['#f87171', '#f87171', '#fbbf24', '#60a5fa', '#34d399'];
    var LABELS = ['Too short', 'Weak', 'Fair', 'Good', 'Strong'];

    function evaluate(pw) {
        var checks = {
            length: pw.length >= 8,
            upper: /[A-Z]/.test(pw),
            lower: /[a-z]/.test(pw),
            digit: /[0-9]/.test(pw),
            special: /[^A-Za-z0-9]/.test(pw)
        };
        var classesMet = [checks.upper, checks.lower, checks.digit, checks.special].filter(Boolean).length;
        var isCommon = COMMON_PASSWORDS.has(pw.toLowerCase());

        // The actual pass/fail rule: 8+ chars and at least 3 of the 4 classes, and not a
        // password that shows up on every "worst passwords of the year" list.
        var meetsMinimum = checks.length && classesMet >= 3 && !isCommon;

        var level;
        if (!checks.length) {
            level = 0;
        } else if (isCommon) {
            level = 1;
        } else {
            var lengthBonus = pw.length >= 12 ? 1 : 0;
            level = Math.max(1, Math.min(4, classesMet - 1 + lengthBonus));
        }

        return { meetsMinimum: meetsMinimum, isCommon: isCommon, level: level };
    }

    // Wires one password <input> to its Pattern-A meter markup: a hint line, a 4-segment bar
    // (id="strengthBar" containing 4 <span> children), and a label (id="strengthLabel").
    // Blocks form submission until the minimum rule is met.
    function attach(inputId, meterId) {
        var input = document.getElementById(inputId);
        var meter = document.getElementById(meterId);
        if (!input || !meter) return;

        var segments = meter.querySelectorAll('.strength-bar span');
        var label = meter.querySelector('.strength-label');
        var form = input.closest('form');
        var lastResult = null;

        input.addEventListener('input', function () {
            var pw = input.value;
            if (!pw) {
                segments.forEach(function (s) { s.style.background = ''; });
                label.textContent = '';
                lastResult = null;
                return;
            }
            lastResult = evaluate(pw);
            var color = COLORS[lastResult.level];
            segments.forEach(function (s, i) {
                s.style.background = i <= lastResult.level ? color : '';
            });
            label.textContent = lastResult.isCommon ? 'Too common - choose something less guessable' : LABELS[lastResult.level];
            label.style.color = color;
        });

        if (form) {
            form.addEventListener('submit', function (e) {
                if (!input.value) return; // required attribute already covers "blank"
                if (!lastResult) lastResult = evaluate(input.value);
                if (!lastResult.meetsMinimum) {
                    e.preventDefault();
                    meter.classList.add('shake');
                    setTimeout(function () { meter.classList.remove('shake'); }, 400);
                    input.focus();
                }
            });
        }
    }

    window.PasswordStrength = { attach: attach };

    // Auto-attach to the conventional ids used on both register.html and resetPassword.html -
    // no separate init call needed on either page.
    document.addEventListener('DOMContentLoaded', function () {
        attach('password', 'passwordStrength');
    });
})();

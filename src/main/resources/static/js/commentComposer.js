// Grows any .composer-textarea as the user types, and enables its paired
// .composer-submit only once there's non-whitespace content. Shared by the
// top-level "Add a Comment" box and every "Add Reply" box (same markup pattern).
document.addEventListener('input', function (event) {
    var ta = event.target;
    if (!ta.classList || !ta.classList.contains('composer-textarea')) {
        return;
    }

    ta.style.height = 'auto';
    ta.style.height = Math.min(ta.scrollHeight, 200) + 'px';

    var form = ta.closest('form');
    var submit = form ? form.querySelector('.composer-submit') : null;
    if (submit) {
        submit.disabled = ta.value.trim().length === 0;
    }
});

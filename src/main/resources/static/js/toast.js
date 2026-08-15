// Animates in the flash-message toast (fragments/toast.html) if the page rendered one, then
// auto-dismisses it - the toast only exists in the DOM at all when a controller actually set a
// "message" flash attribute, so there's nothing to do on a normal page load without one.
document.addEventListener('DOMContentLoaded', function () {
    var toast = document.getElementById('flashToast');
    if (!toast) return;
    animateAndDismiss(toast);
});

function animateAndDismiss(toast, duration) {
    requestAnimationFrame(function () {
        toast.classList.add('show');
    });

    var dismissTimer = setTimeout(dismiss, duration || 3200);

    function dismiss() {
        toast.classList.remove('show');
        toast.addEventListener('transitionend', function remove() {
            toast.removeEventListener('transitionend', remove);
            toast.remove();
        });
    }

    toast.addEventListener('click', function () {
        clearTimeout(dismissTimer);
        dismiss();
    });
}

// Client-side equivalent of the server-rendered flash toast above - for feedback that happens
// without a page reload/redirect (e.g. composeModal.js rejecting an oversized file). Builds the
// exact same markup/classes fragments/toast.html renders server-side (.toast/.toast-error), so
// it looks identical regardless of which path triggered it, then reuses the same animate-in/
// auto-dismiss/click-to-dismiss behavior above. Any page that already loads this script gets
// this for free - fragments/toast.html itself doesn't need to be included, this builds and
// appends its own element rather than relying on that fragment's #flashToast existing.
window.showToast = function (message, isError) {
    var existing = document.getElementById('jsToast');
    if (existing) existing.remove();

    var toast = document.createElement('div');
    toast.id = 'jsToast';
    toast.className = 'toast' + (isError ? ' toast-error' : '');
    toast.setAttribute('role', 'status');
    toast.textContent = message;
    document.body.appendChild(toast);
    animateAndDismiss(toast, isError ? 4000 : 3200);
};

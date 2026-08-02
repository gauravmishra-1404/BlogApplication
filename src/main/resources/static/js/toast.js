// Animates in the flash-message toast (fragments/toast.html) if the page rendered one, then
// auto-dismisses it - the toast only exists in the DOM at all when a controller actually set a
// "message" flash attribute, so there's nothing to do on a normal page load without one.
document.addEventListener('DOMContentLoaded', function () {
    var toast = document.getElementById('flashToast');
    if (!toast) return;

    requestAnimationFrame(function () {
        toast.classList.add('show');
    });

    var dismissTimer = setTimeout(dismiss, 3200);

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
});

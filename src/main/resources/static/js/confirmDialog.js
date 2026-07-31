// Generic replacement for the browser's native confirm() on any delete form - see
// fragments/confirmDialog.html. Any <form data-confirm-title="..."> gets its submit intercepted;
// clicking through the dialog re-submits the same form for real, by default. The raw 'submit'
// event is stopped immediately here (not just preventDefault) so a confirm-gated form is fully
// owned by this dialog until the user actually decides - otherwise another same-node listener
// (e.g. postModal.js's own comment-delete interceptor) would react to the unconfirmed submit
// too and act before the user ever saw the dialog. Confirming dispatches a cancelable
// 'confirmed-submit' event on the form first; another script can preventDefault() that to handle
// the confirmed action itself (fetch + in-place reload, say) instead of a real navigation - if
// nothing cancels it, the native HTMLFormElement.submit() runs, which deliberately does NOT fire
// another 'submit' event, so there's no risk of re-showing the dialog in a loop.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('confirmDialogBackdrop');
    if (!backdrop) return;

    var titleEl = document.getElementById('confirmDialogTitle');
    var messageEl = document.getElementById('confirmDialogMessage');
    var okBtn = document.getElementById('confirmDialogOk');
    var cancelBtn = document.getElementById('confirmDialogCancel');
    var pendingForm = null;

    function open(form) {
        pendingForm = form;
        titleEl.textContent = form.dataset.confirmTitle || 'Are you sure?';
        messageEl.textContent = form.dataset.confirmMessage || 'This action cannot be undone.';
        okBtn.textContent = form.dataset.confirmLabel || 'Delete';
        backdrop.hidden = false;
        document.body.style.overflow = 'hidden';
    }
    function close() {
        backdrop.hidden = true;
        document.body.style.overflow = '';
        pendingForm = null;
    }

    document.addEventListener('submit', function (event) {
        var form = event.target;
        if (!form.dataset.confirmTitle) return;
        event.preventDefault();
        event.stopImmediatePropagation();
        open(form);
    });

    okBtn.addEventListener('click', function () {
        if (!pendingForm) return;
        var form = pendingForm;
        close();
        var confirmedEvent = new CustomEvent('confirmed-submit', { bubbles: true, cancelable: true });
        var notCancelled = form.dispatchEvent(confirmedEvent);
        if (notCancelled) form.submit();
    });
    cancelBtn.addEventListener('click', close);
    backdrop.addEventListener('click', function (e) { if (e.target === backdrop) close(); });
    document.addEventListener('keydown', function (e) { if (e.key === 'Escape' && !backdrop.hidden) close(); });
});

// Wires the "Mark all as read" button on notificationsPage.html. The unread count itself is
// rendered server-side on every page load (GlobalModelAttributes.unreadNotificationCount, backing
// fragments/sidebar.html's #sidebarNotifBadge) - this script only needs to update the DOM after a
// same-page action, not poll for it.
document.addEventListener('DOMContentLoaded', function () {
    var btn = document.getElementById('markAllReadBtn');
    if (!btn) return;

    btn.addEventListener('click', function () {
        btn.disabled = true;
        fetch('/api/notifications/mark-all-read', {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
            .then(function (response) {
                if (!response.ok) throw new Error('mark-all-read failed: ' + response.status);

                document.querySelectorAll('.notif-row.unread').forEach(function (row) {
                    row.classList.remove('unread');
                });

                // The sidebar badge is rendered on this same page too (fragments/sidebar.html) -
                // hide it immediately rather than waiting for a reload/next navigation to pick
                // up the now-zero server-side count.
                var sidebarBadge = document.getElementById('sidebarNotifBadge');
                if (sidebarBadge) sidebarBadge.hidden = true;

                btn.hidden = true;
            })
            .catch(function (error) {
                console.error('Mark all as read failed:', error);
                btn.disabled = false;
            });
    });
});

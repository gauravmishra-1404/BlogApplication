// Closes any open filter dropdown (fragments/filterDropdown.html) when the user clicks
// anywhere outside it - including when opening a different dropdown, so only one stays open.
document.addEventListener('click', function (event) {
    document.querySelectorAll('.filter-multiselect details[open]').forEach(function (details) {
        if (!details.contains(event.target)) {
            details.removeAttribute('open');
        }
    });
});

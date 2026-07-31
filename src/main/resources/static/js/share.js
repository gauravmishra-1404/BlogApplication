// Share/download kebab menu (fragments/shareMenu.html), used on both the dashboard row and
// the post page. Built with <span role="button"> instead of real <button>/<details> elements -
// the dashboard instance sits inside a post-row that is itself one big clickable <a>, and per
// the HTML5 parsing spec, nesting another piece of interactive content (button, a, <details>'s
// summary) inside an open <a> forces the browser to auto-close and reopen that <a> right there,
// silently splitting the post-row card in two (confirmed via a headless-Chrome DOM dump - a
// plain curl of the HTML looked completely fine, since curl never runs the tree-construction
// algorithm a real browser does). Every handler below calls preventDefault/stopPropagation so
// clicks never reach the row's own navigation - available to every visitor, logged in or not,
// unlike reactions. Since a <span> isn't a <button>, Enter/Space activation is wired up by hand.

// Exposed so js/infiniteScroll.js can call this for post-rows it appends after the initial
// load - .options-menu's URL input has no server-side value, only ever set here, so newly
// fetched rows need this run again scoped to just what was added instead of the whole document.
window.BodhSeaShare = {
    populateUrls: function (root) {
        (root || document).querySelectorAll('.options-menu').forEach(function (menu) {
            var input = menu.querySelector('.share-url-input');
            if (input) {
                input.value = window.location.origin + menu.dataset.postUrl;
            }
        });
    },
    // Same gap as populateUrls above, same fix - the Download accordion's confirm-button
    // label/enabled-state depends on how many checkboxes start checked, which is only computed
    // client-side, so any HTML injected after the initial page load (infinite-scroll batches,
    // the post modal) needs this run again scoped to what was added.
    initDownloadConfirms: function (root) {
        (root || document).querySelectorAll('.accordion-body').forEach(function (body) {
            if (body.querySelector('.format-checkbox')) updateDownloadConfirm(body);
        });
    }
};

document.addEventListener('DOMContentLoaded', function () {
    window.BodhSeaShare.populateUrls(document);
    window.BodhSeaShare.initDownloadConfirms(document);
});

function updateDownloadConfirm(accordionBody) {
    var confirmBtn = accordionBody.querySelector('.download-confirm');
    if (!confirmBtn) return;
    var checked = accordionBody.querySelectorAll('.format-checkbox:checked').length;
    confirmBtn.querySelector('.download-confirm-label').textContent =
        'Download selected' + (checked ? ' (' + checked + ')' : '');
    confirmBtn.disabled = checked === 0;
}

// Toggling a checkbox fires 'change', not 'click' - a separate listener, since the click
// handler below only reacts to the fixed set of things a click can mean in this menu.
document.addEventListener('change', function (event) {
    var checkbox = event.target.closest('.format-checkbox');
    if (!checkbox) return;
    updateDownloadConfirm(checkbox.closest('.accordion-body'));
});

// stopPropagation() only stops the event from reaching *other elements* - it does not stop
// other listeners registered on the *same* element for the same phase. js/postModal.js also
// registers a click handler on document (to open a post when it finds a .post-row ancestor),
// and since it's registered later (later script tag), it used to still run right after this
// handler returned - so clicking the kebab opened the post instead of the share/download panel.
// stopImmediatePropagation() also stops sibling listeners on the same node, which is what
// actually fixes it. (Unlike js/profileLinks.js's capture-phase technique for the author link -
// that works there because its capture listener does the whole job itself; a capture-phase
// stopPropagation on document as a *separate* listener would abort the dispatch before it ever
// reaches this handler's own bubble-phase logic below.)
document.addEventListener('click', function (event) {
    var trigger = event.target.closest('.options-trigger');
    if (trigger) {
        event.preventDefault();
        event.stopImmediatePropagation();
        var menu = trigger.closest('.options-menu');
        var panel = menu.querySelector('.share-panel');
        var isOpen = !panel.hidden;
        closeAllMenus();
        if (!isOpen) {
            panel.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            var row = menu.closest('.post-row');
            if (row) row.classList.add('menu-open');
            keepPanelInViewport(panel);
        }
        return;
    }

    // Edit only ever renders (canEdit=true) inside the post-view modal (fragments/postModal.html),
    // which only ever exists on postDashboard.html alongside the compose modal - so reading the
    // post's current data straight out of that already-open modal's own DOM is reliable, and
    // avoids a second fetch for data the page already has.
    var editButton = event.target.closest('[data-edit-post]');
    if (editButton) {
        if (!window.BodhSeaCompose) return; // fallback: let the real editUrl href navigate
        event.preventDefault();
        event.stopImmediatePropagation();
        var postModal = editButton.closest('.post-modal');
        if (!postModal) return;
        var tags = Array.prototype.map.call(
            postModal.querySelectorAll('.blog-tags li'),
            function (li) { return li.textContent.trim(); }
        ).filter(Boolean);
        closeAllMenus();
        var postModalBackdrop = document.getElementById('postModalBackdrop');
        if (postModalBackdrop) postModalBackdrop.hidden = true;
        window.BodhSeaCompose.openForEdit({
            id: postModal.dataset.postId,
            title: postModal.querySelector('.blog-title').textContent.trim(),
            content: postModal.querySelector('.blog-content').textContent.trim(),
            tags: tags
        });
        return;
    }

    var accordionToggle = event.target.closest('[data-share-accordion-toggle]');
    if (accordionToggle) {
        event.preventDefault();
        event.stopImmediatePropagation();
        var body = accordionToggle.nextElementSibling;
        var expanded = accordionToggle.getAttribute('aria-expanded') === 'true';
        accordionToggle.setAttribute('aria-expanded', String(!expanded));
        accordionToggle.classList.toggle('open', !expanded);
        body.hidden = expanded;
        return;
    }

    var platformButton = event.target.closest('[data-share-platform]');
    if (platformButton) {
        event.preventDefault();
        event.stopImmediatePropagation();
        openSharePlatform(platformButton);
        return;
    }

    var copyButton = event.target.closest('.copy-btn');
    if (copyButton) {
        event.preventDefault();
        event.stopImmediatePropagation();
        copyShareLink(copyButton);
        return;
    }

    var confirmButton = event.target.closest('.download-confirm');
    if (confirmButton) {
        event.preventDefault();
        event.stopImmediatePropagation();
        if (!confirmButton.disabled) downloadSelectedFormats(confirmButton);
        return;
    }

    // Any click landing anywhere else inside the menu (its padding, the panel background,
    // between rows) must also never fall through to another script's document-level click
    // handler - same reasoning as every branch above, just with nothing else to do besides
    // stopping it there. Only a click genuinely outside the menu should close it.
    if (event.target.closest('.options-menu')) {
        event.stopImmediatePropagation();
        return;
    }
    closeAllMenus();
});

// role="button" spans don't get free Enter/Space-triggers-click behavior the way a real
// <button> would, so translate those two keys into a synthetic click ourselves.
document.addEventListener('keydown', function (event) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    var target = event.target.closest('[role="button"]');
    if (!target || !target.closest('.options-menu')) return;

    event.preventDefault();
    target.click();
});

// One click can mean downloading several files at once (once a post has more than just PDF -
// see fragments/shareMenu.html). A plain navigation (window.location.href) only survives for
// the *last* URL set that way, since each new navigation cancels whatever the previous one was
// doing - so instead this fires each selected format through its own temporary <a download>,
// same technique a "save all attachments" button on any file-sharing UI uses.
function downloadSelectedFormats(confirmButton) {
    var accordionBody = confirmButton.closest('.accordion-body');
    accordionBody.querySelectorAll('.format-checkbox:checked').forEach(function (checkbox) {
        var link = document.createElement('a');
        link.href = checkbox.dataset.downloadUrl;
        link.download = '';
        document.body.appendChild(link);
        link.click();
        link.remove();
    });
}

function closeAllMenus() {
    document.querySelectorAll('.options-menu .share-panel').forEach(function (panel) {
        panel.hidden = true;
        panel.style.right = '';
        var trigger = panel.previousElementSibling;
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
    });
    document.querySelectorAll('.post-row.menu-open').forEach(function (row) {
        row.classList.remove('menu-open');
    });
}

// .share-panel's CSS anchors it with `right: 0` relative to its own trigger (.options-menu),
// not to the card's true right edge - the trigger sits wherever the reaction bar's content
// naturally ends, which on a narrow viewport is often well short of the screen edge. A fixed
// 260px-wide panel anchored there can overflow off the *left* edge of the screen instead
// (confirmed via a real 375px-viewport measurement: rect.left landed around -105px). Rather
// than assume the trigger's position, this measures the panel after it opens and nudges it
// back on-screen via an inline `right` override - the CSS default stays untouched for every
// case where the trigger already has room.
function keepPanelInViewport(panel) {
    var margin = 8;
    var rect = panel.getBoundingClientRect();
    var currentRight = parseFloat(getComputedStyle(panel).right) || 0;
    if (rect.left < margin) {
        panel.style.right = (currentRight - (margin - rect.left)) + 'px';
    } else if (rect.right > window.innerWidth - margin) {
        panel.style.right = (currentRight + (rect.right - (window.innerWidth - margin))) + 'px';
    }
}

function openSharePlatform(button) {
    var menu = button.closest('.options-menu');
    var fullUrl = window.location.origin + menu.dataset.postUrl;
    var title = menu.dataset.postTitle || '';
    var platform = button.dataset.sharePlatform;
    var shareUrl;

    if (platform === 'whatsapp') {
        shareUrl = 'https://wa.me/?text=' + encodeURIComponent(title + ' ' + fullUrl);
    } else if (platform === 'x') {
        shareUrl = 'https://twitter.com/intent/tweet?url=' + encodeURIComponent(fullUrl) + '&text=' + encodeURIComponent(title);
    } else if (platform === 'linkedin') {
        shareUrl = 'https://www.linkedin.com/sharing/share-offsite/?url=' + encodeURIComponent(fullUrl);
    } else {
        return;
    }

    window.open(shareUrl, '_blank', 'noopener,noreferrer');
}

function copyShareLink(copyButton) {
    var menu = copyButton.closest('.options-menu');
    var input = menu.querySelector('.share-url-input');
    input.select();

    var done = function () {
        var label = copyButton.querySelector('.copy-label');
        var icon = copyButton.querySelector('svg use');
        label.textContent = 'Copied';
        icon.setAttribute('href', '#i-check');
        copyButton.classList.add('copied');
        setTimeout(function () {
            label.textContent = 'Copy';
            icon.setAttribute('href', '#i-copy');
            copyButton.classList.remove('copied');
        }, 1800);
    };

    if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(input.value).then(done).catch(done);
    } else {
        document.execCommand('copy');
        done();
    }
}

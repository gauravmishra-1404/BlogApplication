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

document.addEventListener('DOMContentLoaded', function () {
    document.querySelectorAll('.options-menu').forEach(function (menu) {
        var input = menu.querySelector('.share-url-input');
        if (input) {
            input.value = window.location.origin + menu.dataset.postUrl;
        }
    });
});

document.addEventListener('click', function (event) {
    var trigger = event.target.closest('.options-trigger');
    if (trigger) {
        event.preventDefault();
        event.stopPropagation();
        var menu = trigger.closest('.options-menu');
        var panel = menu.querySelector('.share-panel');
        var isOpen = !panel.hidden;
        closeAllMenus();
        if (!isOpen) {
            panel.hidden = false;
            trigger.setAttribute('aria-expanded', 'true');
            var row = menu.closest('.post-row');
            if (row) row.classList.add('menu-open');
        }
        return;
    }

    var accordionToggle = event.target.closest('[data-share-accordion-toggle]');
    if (accordionToggle) {
        event.preventDefault();
        event.stopPropagation();
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
        event.stopPropagation();
        openSharePlatform(platformButton);
        return;
    }

    var copyButton = event.target.closest('.copy-btn');
    if (copyButton) {
        event.preventDefault();
        event.stopPropagation();
        copyShareLink(copyButton);
        return;
    }

    var downloadButton = event.target.closest('.share-option.download');
    if (downloadButton) {
        event.preventDefault();
        event.stopPropagation();
        window.location.href = downloadButton.dataset.downloadUrl;
        return;
    }

    // Any other click (including inside the open panel, e.g. its padding) shouldn't close the
    // menu - only a click truly outside of it should.
    if (!event.target.closest('.options-menu')) {
        closeAllMenus();
    }
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

function closeAllMenus() {
    document.querySelectorAll('.options-menu .share-panel').forEach(function (panel) {
        panel.hidden = true;
        var trigger = panel.previousElementSibling;
        if (trigger) trigger.setAttribute('aria-expanded', 'false');
    });
    document.querySelectorAll('.post-row.menu-open').forEach(function (row) {
        row.classList.remove('menu-open');
    });
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

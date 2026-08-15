// Post media lightbox (fragments/postMediaGallery.html) - clicking any tile in a post's
// .gallery-single/.gallery-grid collage opens a full-screen slider on top of everything,
// starting at exactly the tile clicked. See the approved design artifact - this replaces the
// old native-scroll carousel, whose only "there's more to see" signal was three faint dots.
//
// One delegated click listener on document, not per-carousel wiring like the old scroll-sync
// version needed - that's what let this drop the MutationObserver dance postModal.js's injected
// HTML used to require. A click handler checks event.target.closest(...) at click time
// regardless of when the element entered the DOM, so postModal.html's fragment (injected after
// page load) and viewPostByID.html's own markup (present at load) both just work, no separate
// init path for either.
document.addEventListener('DOMContentLoaded', function () {
    var lightbox = null, stage, counter, dots, prevBtn, nextBtn;
    var items = [];
    var current = 0;
    var lastFocused = null;

    function build() {
        if (lightbox) return;
        lightbox = document.createElement('div');
        lightbox.className = 'media-lightbox';
        lightbox.setAttribute('role', 'dialog');
        lightbox.setAttribute('aria-modal', 'true');
        lightbox.setAttribute('aria-label', 'Media viewer');
        lightbox.innerHTML =
            '<button type="button" class="media-lightbox-close" aria-label="Close">' +
                '<svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>' +
            '</button>' +
            '<div class="media-lightbox-counter"></div>' +
            '<button type="button" class="media-lightbox-arrow media-lightbox-prev" aria-label="Previous">' +
                '<svg class="icon" aria-hidden="true"><use href="#i-chevron-left"/></svg>' +
            '</button>' +
            '<div class="media-lightbox-stage"></div>' +
            '<button type="button" class="media-lightbox-arrow media-lightbox-next" aria-label="Next">' +
                '<svg class="icon" aria-hidden="true"><use href="#i-chevron-right"/></svg>' +
            '</button>' +
            '<div class="media-lightbox-dots"></div>';
        document.body.appendChild(lightbox);

        stage = lightbox.querySelector('.media-lightbox-stage');
        counter = lightbox.querySelector('.media-lightbox-counter');
        dots = lightbox.querySelector('.media-lightbox-dots');
        prevBtn = lightbox.querySelector('.media-lightbox-prev');
        nextBtn = lightbox.querySelector('.media-lightbox-next');

        lightbox.querySelector('.media-lightbox-close').addEventListener('click', close);
        prevBtn.addEventListener('click', function () { render(current - 1); });
        nextBtn.addEventListener('click', function () { render(current + 1); });
        // Click on the dark backdrop itself (not the stage/media/controls) closes it - clicking
        // the media or any button inside it should never bubble up to this.
        lightbox.addEventListener('click', function (event) {
            if (event.target === lightbox) close();
        });
        document.addEventListener('keydown', function (event) {
            if (!lightbox.classList.contains('open')) return;
            if (event.key === 'Escape') close();
            if (event.key === 'ArrowLeft') render(current - 1);
            if (event.key === 'ArrowRight') render(current + 1);
        });
    }

    function render(index) {
        current = (index + items.length) % items.length;
        var item = items[current];

        // Removing the previous slide's element (rather than just hiding it) is what actually
        // stops a playing video's audio - a video element paused-and-hidden still plays sound.
        stage.innerHTML = '';
        if (item.type === 'VIDEO') {
            var video = document.createElement('video');
            video.src = item.url;
            video.controls = true;
            video.playsInline = true;
            video.autoplay = true;
            stage.appendChild(video);
        } else {
            var img = document.createElement('img');
            img.src = item.url;
            img.alt = '';
            stage.appendChild(img);
        }

        var multiple = items.length > 1;
        counter.textContent = (current + 1) + ' / ' + items.length;
        counter.hidden = !multiple;
        prevBtn.hidden = !multiple;
        nextBtn.hidden = !multiple;
        dots.hidden = !multiple;
        Array.prototype.forEach.call(dots.children, function (dot, i) {
            dot.classList.toggle('active', i === current);
        });
    }

    function open(wrap, startIndex) {
        build();
        items = Array.prototype.map.call(
            wrap.querySelectorAll('.gallery-single[data-media-url], .g-item[data-media-url]'),
            function (el) { return { url: el.dataset.mediaUrl, type: el.dataset.mediaType }; }
        );
        if (!items.length) return;

        dots.innerHTML = '';
        items.forEach(function (_, i) {
            var dot = document.createElement('button');
            dot.type = 'button';
            dot.setAttribute('aria-label', 'Go to item ' + (i + 1));
            dot.addEventListener('click', function () { render(i); });
            dots.appendChild(dot);
        });

        lastFocused = document.activeElement;
        render(startIndex);
        lightbox.classList.add('open');
        document.body.style.overflow = 'hidden';
        lightbox.querySelector('.media-lightbox-close').focus();
    }

    function close() {
        if (!lightbox) return;
        stage.innerHTML = ''; // stop any playing video
        lightbox.classList.remove('open');
        document.body.style.overflow = '';
        if (lastFocused && typeof lastFocused.focus === 'function') lastFocused.focus();
    }

    document.addEventListener('click', function (event) {
        var tile = event.target.closest('.post-view-media .gallery-single, .post-view-media .g-item');
        if (!tile) return;
        var wrap = tile.closest('.post-view-media');
        if (!wrap) return;
        var all = wrap.querySelectorAll('.gallery-single[data-media-url], .g-item[data-media-url]');
        var startIndex = Array.prototype.indexOf.call(all, tile);
        open(wrap, startIndex < 0 ? 0 : startIndex);
    });
});

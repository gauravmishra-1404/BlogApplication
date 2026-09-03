// Landing page motion (templates/landing.html). Two jobs, both purely decorative - the page is
// fully readable and every link works with this file blocked or JS off entirely, which is why
// nothing here creates content, only reveals content the server already rendered.
//
// 1) Staggered scroll reveal, via IntersectionObserver rather than a scroll handler: the browser
//    decides when an element is actually in view, so there's no per-frame work while scrolling.
// 2) The sticky nav condensing once you leave the top of the page.
//
// prefers-reduced-motion is honoured by showing everything immediately instead of animating it -
// the same rule the rest of this app follows (see the loader in dashboardStyle.css).
document.addEventListener('DOMContentLoaded', function () {
    var reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    var revealables = document.querySelectorAll('.reveal');

    if (reduce || !('IntersectionObserver' in window)) {
        // No observer support (or the visitor asked for less motion): show everything as-is.
        // Never leave .reveal elements at opacity:0 - that would hide real content, not an effect.
        revealables.forEach(function (el) { el.classList.add('in'); });
    } else {
        var observer = new IntersectionObserver(function (entries) {
            entries.forEach(function (entry) {
                if (!entry.isIntersecting) return;
                // Stagger siblings so a row of cards arrives in sequence rather than all at once.
                // Modulo keeps the delay bounded no matter how long a section grows.
                var position = Array.prototype.indexOf.call(entry.target.parentNode.children, entry.target);
                setTimeout(function () { entry.target.classList.add('in'); }, (position % 6) * 95);
                observer.unobserve(entry.target);
            });
        }, { threshold: 0.14, rootMargin: '0px 0px -60px 0px' });

        revealables.forEach(function (el) { observer.observe(el); });
    }

    var nav = document.getElementById('lpNav');
    if (nav) {
        var condense = function () { nav.classList.toggle('on', window.scrollY > 40); };
        window.addEventListener('scroll', condense, { passive: true });
        condense();
    }
});

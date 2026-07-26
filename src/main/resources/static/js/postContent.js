// The post body (viewPostByID.html) scrolls inside a fixed-height box with its scrollbar
// hidden via CSS - this only adds the bottom fade-hint, and only when a post is actually
// taller than the clamp. Short/medium posts never get the .has-overflow class, so they
// render with no fade and no visual change at all.
document.addEventListener('DOMContentLoaded', function () {
    var wrap = document.getElementById('blog-content-wrap');
    var content = document.getElementById('blog-content');
    if (!wrap || !content) return;

    if (content.scrollHeight > content.clientHeight + 4) {
        wrap.classList.add('has-overflow');
    }
});

// Dashboard's compose-post modal (opened by the FAB in postDashboard.html instead of navigating
// to /posts/createForm). Unlike js/postModal.js, there's no fetch here - the modal's markup is
// already inline in the page (fragments/composeModal.html), since its content only depends on
// the logged-in user, not on which post was clicked.
//
// The form itself is a normal (non-AJAX) POST - clicking Post/Save navigates to /home on
// success, same as newPost.html/editByPostID.html always have. That's a deliberate scope line
// for this first pass: everything here (title, content, tags, emoji) is fully real; media
// upload and GIF search are shown but intentionally inert (see fragments/composeModal.html).
//
// The same modal doubles as Edit: js/share.js's Edit button calls
// window.BodhSeaCompose.openForEdit() with the post's current data (read straight out of the
// already-open post-view modal's own DOM, not a second fetch), which swaps the form's action
// to /post/republish, fills in the id/title/content/tags, and relabels the chrome. Opening via
// the FAB always resets back to create mode first, since it's the same DOM either way.
document.addEventListener('DOMContentLoaded', function () {
    var backdrop = document.getElementById('composeModalBackdrop');
    if (!backdrop) return;
    // The FAB only exists on the dashboard (nothing to "create" from a profile page) - the
    // modal itself still needs to initialize wherever Edit can open it (see profile.html),
    // so only the FAB's own click-to-create wiring below is conditional on it being present.
    var fab = document.getElementById('composeFabBtn');

    var closeBtn = document.getElementById('composeModalClose');
    var modalTitleText = document.getElementById('composeModalTitleText');
    var form = document.getElementById('composeForm');
    var postIdInput = document.getElementById('composePostId');
    var publishedInput = document.getElementById('composePublished');
    var titleInput = document.getElementById('composeTitle');
    var contentInput = document.getElementById('composeContent');
    var wordCount = document.getElementById('composeWordCount');
    var readTime = document.getElementById('composeReadTime');
    var postBtn = document.getElementById('composePostBtn');
    var postBtnLabel = document.getElementById('composePostBtnLabel');
    var draftBtn = document.getElementById('composeDraftBtn');
    var tagWrap = document.getElementById('composeTagWrap');
    var tagInput = document.getElementById('composeTagInput');
    var tagsHidden = document.getElementById('composeTagsHidden');
    var tagBtn = document.getElementById('composeTagBtn');

    var mediaHidden = document.getElementById('composeMediaHidden');
    var mediaPreviewGrid = document.getElementById('composeMediaPreviewGrid');
    var mediaDropzone = document.getElementById('composeMediaDropzone');
    var mediaDzTitle = document.getElementById('composeMediaDzTitle');
    var mediaFileInput = document.getElementById('composeMediaFileInput');
    var mediaBtn = document.getElementById('composeMediaBtn');

    var emojiBtn = document.getElementById('composeEmojiBtn');
    var emojiPopover = document.getElementById('composeEmojiPopover');
    var emojiCategories = document.getElementById('composeEmojiCategories');
    var emojiSectionLabel = document.getElementById('composeEmojiSectionLabel');
    var emojiGrid = document.getElementById('composeEmojiGrid');
    var emojiSearch = document.getElementById('composeEmojiSearch');
    var emojiSearchClear = document.getElementById('composeEmojiSearchClear');
    var skinToneBtn = document.getElementById('composeSkinToneBtn');
    var emojiConfirmBtn = document.getElementById('composeEmojiConfirm');

    var createAction = form.getAttribute('action');
    var editAction = form.dataset.editAction;

    function openModal() {
        backdrop.hidden = false;
        document.body.style.overflow = 'hidden';
        titleInput.focus();
    }
    function closeModal() {
        backdrop.hidden = true;
        document.body.style.overflow = '';
        closePicker();
    }

    function clearTagChips() {
        tagWrap.querySelectorAll('.tag-chip').forEach(function (chip) { chip.remove(); });
        syncTagsHidden();
    }

    function resetToCreateMode() {
        form.setAttribute('action', createAction);
        // PostDto.id is a primitive int - submitting id="" (this field's default state) fails
        // Spring's data binding with a MethodArgumentNotValidException and breaks the create
        // path entirely. disabled excludes an input from form submission altogether, which is
        // also the semantically correct state: a not-yet-created post has no id to send.
        postIdInput.disabled = true;
        postIdInput.value = '';
        titleInput.value = '';
        contentInput.value = '';
        clearTagChips();
        resetMedia();
        modalTitleText.textContent = 'Create post';
        postBtnLabel.textContent = 'Publish';
        draftBtn.hidden = false;
        autosize();
        updateCounts();
    }

    // Called by js/share.js when Edit is clicked in the post-view modal's kebab menu, and by
    // js/draftRows.js when a row on the Drafts page is clicked. data: { id, title, content,
    // tags: string[], media: [{url, type}] (optional - draftRows.js's own drafts list has no
    // gallery DOM to scrape, so this arrives undefined there and existingMedia just stays
    // empty), isDraft (optional, default false) }. Save Draft only ever shows for a brand-new
    // post or one that's still a draft - an already-published post edited this way only ever
    // gets "Save changes", since Publish is a one-way door (see
    // PostServiceImpl.updatePostByID's own comment) and there's nothing left to "draft" back to.
    window.BodhSeaCompose = {
        openForEdit: function (data) {
            form.setAttribute('action', editAction);
            postIdInput.disabled = false;
            postIdInput.value = data.id;
            titleInput.value = data.title;
            contentInput.value = data.content;
            clearTagChips();
            data.tags.forEach(function (tag) { addTag(tag); });
            resetMedia();
            (data.media || []).forEach(function (m) { addExistingMedia(m.url, m.type); });
            if (data.isDraft) {
                modalTitleText.textContent = 'Edit draft';
                postBtnLabel.textContent = 'Publish';
                draftBtn.hidden = false;
            } else {
                modalTitleText.textContent = 'Edit post';
                postBtnLabel.textContent = 'Save changes';
                draftBtn.hidden = true;
            }
            autosize();
            updateCounts();
            openModal();
        }
    };

    if (fab) {
        fab.addEventListener('click', function () {
            resetToCreateMode();
            openModal();
        });
    }
    closeBtn.addEventListener('click', closeModal);
    backdrop.addEventListener('click', function (e) { if (e.target === backdrop) closeModal(); });

    // Both footer actions are real submits of the same form - which one fires just sets
    // "published" first, so PostServiceImpl.save()/updatePostByID() know whether to enforce the
    // full title/content/tags requirement (Publish) or accept whatever's there so far (Save
    // draft). Plain buttons rather than one submit + a name/value pair, since a disabled
    // submit button's own value is never sent at all, exactly the state Publish starts in
    // before there's a title/content/tag to submit.
    draftBtn.addEventListener('click', function () {
        publishedInput.value = 'false';
        form.requestSubmit();
    });
    postBtn.addEventListener('click', function () {
        publishedInput.value = 'true';
        form.requestSubmit();
    });
    document.addEventListener('keydown', function (e) {
        if (e.key !== 'Escape' || backdrop.hidden) return;
        if (emojiPopover.classList.contains('open')) { closePicker(); return; }
        closeModal();
    });

    // ---------- title/content ----------
    function autosize() {
        contentInput.style.height = 'auto';
        contentInput.style.height = contentInput.scrollHeight + 'px';
    }
    function updateCounts() {
        var text = contentInput.value.trim();
        var words = text.length ? text.split(/\s+/).length : 0;
        wordCount.textContent = words + (words === 1 ? ' word' : ' words');
        var mins = Math.max(1, Math.round(words / 200));
        readTime.textContent = (words === 0 ? '0' : mins) + ' min read';
        updatePostState();
    }
    function updatePostState() {
        // Tags are required the same as title/content - see PostServiceImpl.save()'s
        // server-side check, which is the one that actually matters; this is just the UX
        // so the button reflects that before a submit round-trip finds out the hard way.
        // anyMediaUploading() (declared below, safe to call here - function declarations are
        // hoisted) also gates both buttons: submitting while a file is still mid-upload would
        // publish a post missing whatever hadn't finished yet.
        postBtn.disabled = !(titleInput.value.trim() && contentInput.value.trim() && tagsHidden.value.trim()) || anyMediaUploading();
        draftBtn.disabled = anyMediaUploading();
    }
    contentInput.addEventListener('input', function () { autosize(); updateCounts(); });
    titleInput.addEventListener('input', updatePostState);

    // ---------- tags ----------
    function syncTagsHidden() {
        var values = Array.prototype.map.call(tagWrap.querySelectorAll('.tag-chip'), function (chip) {
            return chip.dataset.value;
        });
        tagsHidden.value = values.join(',');
        updatePostState();
    }
    function addTag(value) {
        value = value.trim().replace(/^#/, '');
        if (!value) return;
        var chip = document.createElement('span');
        chip.className = 'tag-chip';
        chip.dataset.value = value;
        var label = document.createElement('span');
        label.textContent = value;
        var remove = document.createElement('button');
        remove.type = 'button';
        remove.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>';
        remove.addEventListener('click', function () { chip.remove(); syncTagsHidden(); });
        chip.appendChild(label);
        chip.appendChild(remove);
        tagWrap.insertBefore(chip, tagInput);
        tagInput.value = '';
        syncTagsHidden();
    }
    tagInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            addTag(tagInput.value);
        } else if (e.key === 'Backspace' && !tagInput.value) {
            var chips = tagWrap.querySelectorAll('.tag-chip');
            if (chips.length) { chips[chips.length - 1].remove(); syncTagsHidden(); }
        }
    });
    tagBtn.addEventListener('click', function () { tagInput.focus(); });

    // ---------- media (photo/video upload) ----------
    // Client-side mirror of S3MediaUploadService's own allowlist/limits (server is still the
    // real authority - this is just fast feedback before ever making a network call). One
    // upload happens per file: POST /api/media/presign gets a short-lived S3 PUT URL back, the
    // browser PUTs the raw file straight to S3 (never through this app's own server), and only
    // the resulting CloudFront URL gets kept - in mediaItems, then in the hidden "mediaJson"
    // field the real form submit carries.
    var MEDIA_MAX_IMAGES = 4;
    // A MIN as well as a MAX for both - not just a ceiling like most upload limits. Enforced
    // client-side only: the browser uploads straight to S3 via a presigned URL (see
    // S3MediaUploadService), so this app's server never sees the file's bytes at all and can't
    // check size server-side the way it checks content-type. A presigned POST with a policy
    // document could enforce this at the S3 level too; presigned PUT (what's used here, simpler
    // to implement) can't.
    var MEDIA_MIN_IMAGE_BYTES = 10 * 1024;
    var MEDIA_MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    var MEDIA_MIN_VIDEO_BYTES = 5 * 1024 * 1024;
    var MEDIA_MAX_VIDEO_BYTES = 100 * 1024 * 1024;
    var MEDIA_ALLOWED_TYPES = {
        'image/jpeg': 'IMAGE', 'image/png': 'IMAGE', 'image/webp': 'IMAGE', 'image/gif': 'IMAGE',
        'video/mp4': 'VIDEO', 'video/webm': 'VIDEO', 'video/quicktime': 'VIDEO'
    };
    var mediaItems = [];
    var mediaLocalIdSeq = 0;

    // Includes its own unit rather than a hardcoded "MB" at the call site - MEDIA_MIN_IMAGE_BYTES
    // dropping to 10KB meant a plain mb() (always dividing by 1024*1024) would round that all the
    // way down to "0MB", a genuinely confusing rejection message ("images need to be at least
    // 0MB"). Anything under 1MB shows in KB instead.
    function formatBytes(bytes) {
        if (bytes < 1024 * 1024) {
            return Math.round(bytes / 1024) + 'KB';
        }
        return (bytes / (1024 * 1024)).toFixed(1).replace(/\.0$/, '') + 'MB';
    }

    // window.showToast (js/toast.js) - the same toast component the rest of the app already
    // uses for server-driven confirmations ("Post published" etc.), made callable from plain
    // JS since rejecting a file happens entirely client-side, no redirect to hang a flash
    // attribute off of.
    function showMediaError(message) {
        window.showToast(message, true);
    }

    function resetMedia() {
        mediaItems.forEach(function (item) {
            if (item.xhr && item.uploading) item.xhr.abort();
            if (item.previewUrl && item.previewUrl.indexOf('blob:') === 0) URL.revokeObjectURL(item.previewUrl);
        });
        mediaItems = [];
        renderMedia();
    }

    // Edit mode only - a post's already-uploaded media, read out of the post-view modal's DOM
    // by js/share.js (same "scrape the DOM, no second fetch" pattern already used for
    // title/content/tags). Nothing to upload here, remoteUrl is already the real CloudFront URL.
    function addExistingMedia(url, type) {
        mediaItems.push({ localId: ++mediaLocalIdSeq, kind: type, previewUrl: url, remoteUrl: url, uploading: false, failed: false, progress: 0, xhr: null });
        renderMedia();
    }

    function currentMediaKind() {
        var live = mediaItems.filter(function (i) { return !i.failed; });
        return live.length ? live[0].kind : null;
    }

    // Images OR one video, never mixed - a new file that doesn't match what's already selected
    // (or a second video, or a 5th image) is rejected with a visible reason, same "immediate
    // feedback on every interaction" principle this project applies everywhere else - a
    // min-size floor especially needs this, since a silently-ignored file that LOOKED fine
    // (a clean, well-compressed image) would otherwise be genuinely confusing.
    function addFiles(fileList) {
        Array.prototype.forEach.call(fileList, function (file) {
            var kind = MEDIA_ALLOWED_TYPES[file.type];
            if (!kind) {
                showMediaError('"' + file.name + '" isn\'t a supported photo or video format.');
                return;
            }
            var existingKind = currentMediaKind();
            if (existingKind && existingKind !== kind) {
                showMediaError(existingKind === 'VIDEO' ? 'This post already has a video - remove it first to add photos.' : 'This post already has photos - remove them first to add a video.');
                return;
            }
            var liveCount = mediaItems.filter(function (i) { return !i.failed; }).length;
            if (kind === 'VIDEO' && liveCount >= 1) {
                showMediaError('Only one video per post.');
                return;
            }
            if (kind === 'IMAGE' && liveCount >= MEDIA_MAX_IMAGES) {
                showMediaError('Up to ' + MEDIA_MAX_IMAGES + ' photos per post.');
                return;
            }
            var minBytes = kind === 'VIDEO' ? MEDIA_MIN_VIDEO_BYTES : MEDIA_MIN_IMAGE_BYTES;
            var maxBytes = kind === 'VIDEO' ? MEDIA_MAX_VIDEO_BYTES : MEDIA_MAX_IMAGE_BYTES;
            if (file.size < minBytes) {
                showMediaError('"' + file.name + '" is too small - ' + kind.toLowerCase() + 's need to be at least ' + formatBytes(minBytes) + '.');
                return;
            }
            if (file.size > maxBytes) {
                showMediaError('"' + file.name + '" is too large - ' + kind.toLowerCase() + 's can be at most ' + formatBytes(maxBytes) + '.');
                return;
            }

            var item = {
                localId: ++mediaLocalIdSeq, kind: kind, previewUrl: URL.createObjectURL(file),
                remoteUrl: null, uploading: true, failed: false, progress: 0, xhr: null
            };
            mediaItems.push(item);
            renderMedia();
            uploadFile(file, item);
        });
    }

    function uploadFile(file, item) {
        fetch('/api/media/presign?contentType=' + encodeURIComponent(file.type), {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
            .then(function (response) {
                // 503 = RestMediaController's own MediaUploadUnavailableException - AWS media
                // storage isn't configured on this environment yet (still true today, pending
                // CloudFront account verification), not a problem with this specific file.
                if (response.status === 503) {
                    showMediaError('Photo/video upload isn\'t available yet - check back soon.');
                    throw new Error('media upload unavailable');
                }
                if (!response.ok) throw new Error('presign failed: ' + response.status);
                return response.json();
            })
            .then(function (presigned) {
                var xhr = new XMLHttpRequest();
                item.xhr = xhr;
                xhr.open('PUT', presigned.uploadUrl);
                xhr.setRequestHeader('Content-Type', file.type);
                xhr.upload.onprogress = function (e) {
                    if (!e.lengthComputable) return;
                    item.progress = Math.round((e.loaded / e.total) * 100);
                    var fill = mediaPreviewGrid.querySelector('[data-media-id="' + item.localId + '"] .media-upload-bar-fill');
                    if (fill) fill.style.width = item.progress + '%';
                };
                xhr.onload = function () {
                    item.uploading = false;
                    if (xhr.status >= 200 && xhr.status < 300) {
                        item.remoteUrl = presigned.publicUrl;
                    } else {
                        item.failed = true;
                    }
                    renderMedia();
                };
                xhr.onerror = function () {
                    item.uploading = false;
                    item.failed = true;
                    renderMedia();
                };
                xhr.send(file);
            })
            .catch(function () {
                item.uploading = false;
                item.failed = true;
                renderMedia();
            });
    }

    // Removing a still-uploading file aborts that specific request rather than letting it
    // finish just to discard the result - matches the approved design's own annotation for
    // this exact interaction.
    function removeMediaItem(localId) {
        var idx = mediaItems.findIndex(function (i) { return i.localId === localId; });
        if (idx === -1) return;
        var item = mediaItems[idx];
        if (item.xhr && item.uploading) item.xhr.abort();
        if (item.previewUrl && item.previewUrl.indexOf('blob:') === 0) URL.revokeObjectURL(item.previewUrl);
        mediaItems.splice(idx, 1);
        renderMedia();
    }

    function renderMedia() {
        mediaPreviewGrid.innerHTML = '';
        mediaPreviewGrid.hidden = mediaItems.length === 0;

        mediaItems.forEach(function (item) {
            var cell = document.createElement('div');
            cell.className = 'media-preview-item' + (item.failed ? ' upload-failed' : '');
            cell.setAttribute('data-media-id', item.localId);

            if (item.kind === 'IMAGE') {
                var img = document.createElement('img');
                img.src = item.previewUrl;
                cell.appendChild(img);
            } else {
                var video = document.createElement('video');
                video.src = item.previewUrl;
                video.muted = true;
                cell.appendChild(video);
                var play = document.createElement('span');
                play.className = 'g-play';
                play.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-play"/></svg>';
                cell.appendChild(play);
            }

            if (item.uploading) {
                var bar = document.createElement('div');
                bar.className = 'media-upload-bar';
                var fill = document.createElement('div');
                fill.className = 'media-upload-bar-fill';
                fill.style.width = (item.progress || 0) + '%';
                bar.appendChild(fill);
                cell.appendChild(bar);
            }

            var removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.className = 'media-preview-remove';
            removeBtn.setAttribute('aria-label', 'Remove');
            removeBtn.innerHTML = '<svg class="icon" aria-hidden="true"><use href="#i-close"/></svg>';
            removeBtn.addEventListener('click', function () { removeMediaItem(item.localId); });
            cell.appendChild(removeBtn);

            mediaPreviewGrid.appendChild(cell);
        });

        var kind = currentMediaKind();
        var liveCount = mediaItems.filter(function (i) { return !i.failed; }).length;
        mediaDropzone.classList.toggle('compact', mediaItems.length > 0);
        if (liveCount === 0) {
            mediaDzTitle.textContent = 'Drop photos or a video, or click to browse';
            mediaDropzone.hidden = false;
        } else if (kind === 'VIDEO' || liveCount >= MEDIA_MAX_IMAGES) {
            mediaDropzone.hidden = true; // nothing more can be added right now
        } else {
            mediaDropzone.hidden = false;
            var remaining = MEDIA_MAX_IMAGES - liveCount;
            mediaDzTitle.textContent = 'Add up to ' + remaining + ' more photo' + (remaining === 1 ? '' : 's');
        }

        syncMediaHidden();
        updatePostState();
    }

    function syncMediaHidden() {
        var payload = mediaItems
            .filter(function (i) { return !i.failed && i.remoteUrl; })
            .map(function (i) { return { url: i.remoteUrl, type: i.kind }; });
        mediaHidden.value = payload.length ? JSON.stringify(payload) : '';
    }

    function anyMediaUploading() {
        return mediaItems.some(function (i) { return i.uploading; });
    }

    mediaDropzone.addEventListener('click', function () { mediaFileInput.click(); });
    mediaBtn.addEventListener('click', function () { mediaFileInput.click(); });
    mediaFileInput.addEventListener('change', function () {
        addFiles(this.files);
        this.value = '';
    });
    mediaDropzone.addEventListener('dragover', function (e) { e.preventDefault(); mediaDropzone.classList.add('drag-over'); });
    mediaDropzone.addEventListener('dragleave', function () { mediaDropzone.classList.remove('drag-over'); });
    mediaDropzone.addEventListener('drop', function (e) {
        e.preventDefault();
        mediaDropzone.classList.remove('drag-over');
        if (e.dataTransfer && e.dataTransfer.files) addFiles(e.dataTransfer.files);
    });

    // ---------- emoji picker ----------
    // Full Unicode emoji rendered natively by the browser/OS font - no library needed. Keyword
    // map only covers a curated subset; unmapped emoji are still browsable by category.
    var EMOJI_CATEGORIES = [
        { key: 'smileys', icon: '😀', label: 'Smileys & people',
            emojis: ['😀','😃','😄','😁','😆','😅','🤣','😂','🙂','🙃','😉','😊','😇','🥰','😍','🤩','😘','😋','😛','🤪','😝','🤑','🤗','🤔','🤐','😐','😏','🙄','😬','🤥','😴','🤤','😷','🤒','🤢','🥵','😎','🤓','😕','😢','😭','😡','🤬','😱','🥺','👍','👎','👏','🙌','🤝','🙏','✍️','💪','🥹'] },
        { key: 'animals', icon: '🐻', label: 'Animals & nature',
            emojis: ['🐶','🐱','🐭','🐹','🐰','🦊','🐻','🐼','🐨','🐯','🦁','🐮','🐷','🐸','🐵','🙈','🙉','🙊','🐔','🐧','🐦','🦆','🦉','🦇','🐺','🐴','🦄','🐝','🦋','🐢','🐍','🦎','🐙','🦀','🐬','🐳','🦈','🌵','🌲','🌴','🌸','🌻','🌈','☀️','🌙','⭐','🔥','💧','🌊','🍃'] },
        { key: 'food', icon: '🍔', label: 'Food & drink',
            emojis: ['🍏','🍎','🍊','🍋','🍌','🍉','🍇','🍓','🥑','🍍','🥕','🌽','🥐','🍞','🧀','🥚','🥓','🍔','🍟','🍕','🌭','🌮','🌯','🥗','🍝','🍜','🍣','🍱','🍤','🍦','🍩','🍪','🎂','🍫','🍿','🍺','☕','🍵','🥤','🧋'] },
        { key: 'activities', icon: '⚽', label: 'Activities',
            emojis: ['⚽','🏀','🏈','⚾','🎾','🏐','🏓','🏸','🏒','🥊','🎣','🎯','🎳','🎮','🎲','🎨','🎬','🎤','🎧','🎼','🎹','🎸','🎻','🏆','🥇','🏋️','🏄','🏊','🚴','🧘'] },
        { key: 'travel', icon: '✈️', label: 'Travel & places',
            emojis: ['🚗','🚕','🚙','🚌','🚓','🚑','🚒','🚚','🏍️','🚲','🛴','✈️','🚀','🚁','🚢','⛵','🚂','🚆','🚇','🚦','🗺️','🗽','🗼','🏰','🎡','🎢','🏖️','🏝️','🌋','⛰️','🏔️','🏕️','🏠','🏢','⛪','🕌','🛕'] },
        { key: 'objects', icon: '💡', label: 'Objects',
            emojis: ['⌚','📱','💻','🖥️','📷','📸','🎥','📺','📻','💡','🔦','🕯️','💰','💵','💳','💎','🔧','🔨','⚙️','🔒','🔑','💊','💉','🩹','🚪','🛏️','🚿','🧴','🧻','🛒','📚'] },
        { key: 'symbols', icon: '❤️', label: 'Symbols',
            emojis: ['❤️','🧡','💛','💚','💙','💜','🖤','🤍','💔','💕','💞','💓','💖','💘','✨','⚡','☮️','✝️','☪️','🕉️','☯️','🔥','❌','✅','❗','❓','💯','♻️','⚠️','🔞'] },
        { key: 'flags', icon: '🏳️', label: 'Flags',
            emojis: ['🏳️','🏴','🚩','🏁','🏳️‍🌈','🇮🇳','🇺🇸','🇬🇧','🇨🇦','🇦🇺','🇩🇪','🇫🇷','🇯🇵','🇰🇷','🇧🇷','🇮🇹','🇪🇸','🇳🇱','🇿🇦'] }
    ];
    var EMOJI_KEYWORDS = {
        '😀':'grin smile happy', '😂':'laugh lol funny cry', '🤣':'rofl laugh funny', '😍':'love heart eyes',
        '😘':'kiss love', '😎':'cool sunglasses', '🤔':'think hmm', '😢':'sad cry', '😭':'cry sad sob',
        '😡':'angry mad rage', '🤬':'angry swear', '😱':'scared shock', '🥺':'pleading please cute',
        '👍':'thumbs up yes good', '👎':'thumbs down no bad', '👏':'clap applause', '🙌':'praise hooray celebrate',
        '🤝':'handshake deal agree', '🙏':'pray thanks please', '💪':'strong muscle flex', '🔥':'fire hot lit',
        '💯':'hundred perfect', '✨':'sparkle shine magic', '❤️':'love heart red', '💔':'heartbreak sad breakup',
        '🐶':'dog puppy', '🐱':'cat kitten', '🦊':'fox', '🐻':'bear', '🐼':'panda', '🦁':'lion',
        '🌊':'wave ocean sea water bodh', '🍃':'leaf nature wind bodh', '☀️':'sun sunny weather',
        '🌙':'moon night', '⭐':'star', '🌈':'rainbow', '💧':'water drop tear',
        '🍏':'apple fruit', '🍕':'pizza food', '🍔':'burger food', '🍩':'donut food sweet', '☕':'coffee drink',
        '🍺':'beer drink', '🎂':'cake birthday', '🍿':'popcorn movie',
        '⚽':'football soccer', '🏀':'basketball', '🎮':'game gaming', '🎨':'art paint', '🎬':'movie film',
        '🎤':'mic sing karaoke', '🏆':'trophy win winner', '🎸':'guitar music',
        '🚗':'car drive', '✈️':'plane flight travel', '🚀':'rocket launch space', '🚢':'ship boat',
        '🏖️':'beach travel vacation', '🌋':'volcano', '🏔️':'mountain travel',
        '📱':'phone mobile', '💻':'laptop computer', '📷':'camera photo', '💡':'idea bulb light',
        '💰':'money cash rich', '💎':'diamond gem', '🔒':'lock secure', '🔑':'key unlock',
        '✅':'check done correct yes', '❌':'cross wrong no', '❗':'important alert', '❓':'question confused',
        '⚠️':'warning caution', '🚩':'flag red mark', '🇮🇳':'india flag', '🇺🇸':'usa america flag',
        '😴':'sleep tired', '🤒':'sick ill', '🥵':'hot sweat', '🤓':'nerd glasses study'
    };

    var activeCategory = EMOJI_CATEGORIES[0].key;
    var emojiQuery = '';
    var skinTones = ['👋','👋🏻','👋🏼','👋🏽','👋🏾','👋🏿'];
    var skinToneIndex = 0;

    function renderCategoryTabs() {
        emojiCategories.innerHTML = '';
        EMOJI_CATEGORIES.forEach(function (cat) {
            var b = document.createElement('button');
            b.type = 'button';
            b.className = 'emoji-cat-btn' + (cat.key === activeCategory && !emojiQuery ? ' active' : '');
            b.textContent = cat.icon;
            b.title = cat.label;
            b.addEventListener('click', function () {
                activeCategory = cat.key;
                emojiQuery = '';
                emojiSearch.value = '';
                emojiSearchClear.classList.remove('show');
                renderCategoryTabs();
                renderEmojiGrid();
            });
            emojiCategories.appendChild(b);
        });
    }

    function renderEmojiGrid() {
        emojiGrid.innerHTML = '';
        var list;
        if (emojiQuery) {
            var q = emojiQuery.toLowerCase();
            list = [];
            EMOJI_CATEGORIES.forEach(function (cat) {
                cat.emojis.forEach(function (e) {
                    if (list.indexOf(e) === -1 && (EMOJI_KEYWORDS[e] || '').indexOf(q) !== -1) list.push(e);
                });
            });
            emojiSectionLabel.textContent = 'Search results';
        } else {
            var current = EMOJI_CATEGORIES.filter(function (c) { return c.key === activeCategory; })[0];
            list = current.emojis;
            emojiSectionLabel.textContent = current.label;
        }
        if (!list.length) {
            var empty = document.createElement('div');
            empty.className = 'picker-empty';
            empty.textContent = 'No emojis found for "' + emojiQuery + '"';
            emojiGrid.appendChild(empty);
            return;
        }
        list.forEach(function (e) {
            var b = document.createElement('button');
            b.type = 'button';
            b.textContent = e;
            b.addEventListener('click', function () { insertAtCursor(e); });
            emojiGrid.appendChild(b);
        });
    }

    emojiSearch.addEventListener('input', function () {
        emojiQuery = this.value.trim();
        emojiSearchClear.classList.toggle('show', !!emojiQuery);
        renderCategoryTabs();
        renderEmojiGrid();
    });
    emojiSearchClear.addEventListener('click', function () {
        emojiQuery = '';
        emojiSearch.value = '';
        emojiSearchClear.classList.remove('show');
        renderCategoryTabs();
        renderEmojiGrid();
    });
    skinToneBtn.addEventListener('click', function () {
        skinToneIndex = (skinToneIndex + 1) % skinTones.length;
        skinToneBtn.textContent = skinTones[skinToneIndex];
    });
    emojiConfirmBtn.addEventListener('click', function () { closePicker(); });

    function insertAtCursor(text) {
        var start = contentInput.selectionStart || contentInput.value.length;
        var end = contentInput.selectionEnd || contentInput.value.length;
        var value = contentInput.value;
        contentInput.value = value.slice(0, start) + text + value.slice(end);
        var caret = start + text.length;
        contentInput.focus();
        contentInput.setSelectionRange(caret, caret);
        autosize();
        updateCounts();
    }

    function openPicker() {
        emojiPopover.classList.add('open');
        emojiBtn.setAttribute('aria-expanded', 'true');
    }
    function closePicker() {
        emojiPopover.classList.remove('open');
        emojiBtn.setAttribute('aria-expanded', 'false');
    }
    emojiBtn.addEventListener('click', function (e) {
        e.stopPropagation();
        emojiPopover.classList.contains('open') ? closePicker() : openPicker();
    });
    emojiPopover.addEventListener('click', function (e) { e.stopPropagation(); });
    document.addEventListener('click', function () { closePicker(); });

    renderCategoryTabs();
    renderEmojiGrid();
    updateCounts();
});

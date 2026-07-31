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
    var titleInput = document.getElementById('composeTitle');
    var contentInput = document.getElementById('composeContent');
    var wordCount = document.getElementById('composeWordCount');
    var readTime = document.getElementById('composeReadTime');
    var postBtn = document.getElementById('composePostBtn');
    var postBtnLabel = document.getElementById('composePostBtnLabel');
    var tagWrap = document.getElementById('composeTagWrap');
    var tagInput = document.getElementById('composeTagInput');
    var tagsHidden = document.getElementById('composeTagsHidden');
    var tagBtn = document.getElementById('composeTagBtn');

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
        modalTitleText.textContent = 'Create post';
        postBtnLabel.textContent = 'Post';
        autosize();
        updateCounts();
    }

    // Called by js/share.js when Edit is clicked in the post-view modal's kebab menu.
    // data: { id, title, content, tags: string[] }
    window.BodhSeaCompose = {
        openForEdit: function (data) {
            form.setAttribute('action', editAction);
            postIdInput.disabled = false;
            postIdInput.value = data.id;
            titleInput.value = data.title;
            contentInput.value = data.content;
            clearTagChips();
            data.tags.forEach(function (tag) { addTag(tag); });
            modalTitleText.textContent = 'Edit post';
            postBtnLabel.textContent = 'Save changes';
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
        postBtn.disabled = !(titleInput.value.trim() && contentInput.value.trim() && tagsHidden.value.trim());
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

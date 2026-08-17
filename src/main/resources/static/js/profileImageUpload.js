// Shared by avatarEditor.js and coverEditor.js's own "Photo" tab handlers - presigns straight to
// S3 the same way js/composeModal.js does for post media (POST /api/media/presign-profile-image
// instead of /api/media/presign, and a "kind" of "avatar"/"cover" instead of a post-media type),
// then PUTs the file directly, this app's server never receiving the bytes.
//
// Exposed as window.profileImageUpload rather than a module export - this app has no bundler,
// every other page script is a plain global-scope <script>, matching that convention.
window.profileImageUpload = (function () {
    var MAX_BYTES = 2 * 1024 * 1024; // 2MB - matches ProfileController's old MAX_AVATAR_BYTES

    function formatBytes(n) {
        return (n / (1024 * 1024)).toFixed(1) + 'MB';
    }

    /**
     * @param file        the File the user picked
     * @param kind        "avatar" or "cover"
     * @param callbacks   { onStart, onProgress(percent), onSuccess(publicUrl), onError(message) }
     */
    function upload(file, kind, callbacks) {
        if (file.size > MAX_BYTES) {
            callbacks.onError('That file is too large - photos can be at most ' + formatBytes(MAX_BYTES) + '.');
            return;
        }
        if (!file.type || file.type.indexOf('image/') !== 0) {
            callbacks.onError('Please choose an image file.');
            return;
        }

        callbacks.onStart();

        fetch('/api/media/presign-profile-image?contentType=' + encodeURIComponent(file.type) + '&kind=' + kind, {
            method: 'POST',
            headers: { 'X-Requested-With': 'XMLHttpRequest' }
        })
            .then(function (response) {
                // 503 = RestMediaController's own MediaUploadUnavailableException - AWS media
                // storage isn't configured on this environment yet (DisabledMediaUploadService is
                // the active bean), not a problem with this specific file - same distinction
                // composeModal.js's uploadFile already makes.
                if (response.status === 503) {
                    throw new Error('Photo upload isn\'t available on this environment yet.');
                }
                if (!response.ok) {
                    throw new Error('Could not start the upload - please try again.');
                }
                return response.json();
            })
            .then(function (presigned) {
                var xhr = new XMLHttpRequest();
                xhr.open('PUT', presigned.uploadUrl);
                xhr.setRequestHeader('Content-Type', file.type);
                xhr.upload.onprogress = function (e) {
                    if (!e.lengthComputable) return;
                    callbacks.onProgress(Math.round((e.loaded / e.total) * 100));
                };
                xhr.onload = function () {
                    if (xhr.status >= 200 && xhr.status < 300) {
                        callbacks.onSuccess(presigned.publicUrl);
                    } else {
                        callbacks.onError('Upload failed - please try again.');
                    }
                };
                xhr.onerror = function () {
                    callbacks.onError('Upload failed - please try again.');
                };
                xhr.send(file);
            })
            .catch(function (err) {
                callbacks.onError(err.message || 'Upload failed - please try again.');
            });
    }

    return { upload: upload };
})();

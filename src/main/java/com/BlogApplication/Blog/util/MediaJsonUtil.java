package com.BlogApplication.Blog.util;

import com.BlogApplication.Blog.models.PostMedia;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns a post's media list into the same {url, type} JSON shape composeModal.js already
 * builds and submits (see PostServiceImpl.resolveMedia) - used from templates via
 * T(...).toJson(...), same "static utility callable straight from Thymeleaf" pattern
 * TimeFormatter/NumberFormatter/AvatarPresets already establish. draftsPage.html uses this to
 * put a draft's existing media into a data attribute (js/draftRows.js reads it back out when
 * opening that draft for edit) - a compact row list has no full gallery DOM to scrape the way
 * js/share.js scrapes a published post's already-rendered carousel instead.
 */
public class MediaJsonUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MediaJsonUtil() {
    }

    public static String toJson(List<PostMedia> media) {
        if (media == null || media.isEmpty()) {
            return "";
        }
        try {
            List<Map<String, String>> simplified = media.stream()
                    .map(m -> Map.of("url", m.getMediaUrl(), "type", m.getMediaType()))
                    .collect(Collectors.toList());
            return MAPPER.writeValueAsString(simplified);
        } catch (Exception e) {
            // Never worth breaking the drafts list render over - a draft just opens for edit
            // with no prefilled media in the (extremely unlikely) event this fails.
            return "";
        }
    }
}

package com.BlogApplication.Blog.util;

import com.BlogApplication.Blog.models.User;

import java.util.List;

/**
 * The 8 built-in cover-banner scenes (original SVG illustrations under "#cover-{key}" in the
 * icon sprite, not stock photos), plus the plain-gradient option shared with AvatarPresets. As
 * with avatars, every gradient a User ends up with is generated server-side from a validated
 * swatch index or hue - a client never gets to store raw CSS.
 */
public class CoverPresets {

    private CoverPresets() {
    }

    // "icon" and "swatchCss" back the small picker tile (an icon-on-gradient swatch, the same
    // shape as the avatar preset picker) - the full illustrated scene (icon="#cover-{key}" in
    // the sprite) only renders correctly at the large preview's size; nested <use> of a scene
    // symbol at a tiny grid-tile size mis-scales its internal shapes in some browsers, so the
    // tiles get the simpler, proven icon+gradient treatment instead. "swatchCss" mirrors each
    // scene's own sky gradient so the tile still hints at the actual scene.
    public record Scene(String key, String label, String icon, String swatchCss) {
    }

    public static final List<Scene> SCENES = List.of(
            new Scene("dawn", "Dawn Tide", "sun", "linear-gradient(135deg, #fcd9a8 0%, #5b8def 100%)"),
            new Scene("deep", "Deep Current", "anchor", "linear-gradient(135deg, #16222a 0%, #3a6073 100%)"),
            new Scene("moonlit", "Moonlit Bay", "moon", "linear-gradient(135deg, #0b101b 0%, #1d2740 100%)"),
            new Scene("golden", "Golden Hour", "sun", "linear-gradient(135deg, #fda085 0%, #f6d365 100%)"),
            new Scene("misty", "Misty Peaks", "compass", "linear-gradient(135deg, #c9d6e3 0%, #8aa9d0 100%)"),
            new Scene("lantern", "Lantern Night", "lotus", "linear-gradient(135deg, #2d1b45 0%, #a18cd1 100%)"),
            new Scene("seafoam", "Seafoam", "wave", "linear-gradient(135deg, #43e97b 0%, #2fb8af 100%)"),
            new Scene("sunrise", "Sunrise", "shell", "linear-gradient(135deg, #ff9a8b 0%, #ffecd2 100%)")
    );

    public static boolean isValidSceneKey(String key) {
        return key != null && SCENES.stream().anyMatch(s -> s.key().equals(key));
    }

    // Only "preset" needs a background color at all (a solid color would show through the
    // scene's transparent edges); "photo" and "color" modes don't use this.
    public static String gradientStyle(User user) {
        if (user == null || !"color".equals(user.getCoverType())
                || user.getCoverGradient() == null || user.getCoverGradient().isBlank()) {
            return "";
        }
        return "background:" + user.getCoverGradient() + ";";
    }
}

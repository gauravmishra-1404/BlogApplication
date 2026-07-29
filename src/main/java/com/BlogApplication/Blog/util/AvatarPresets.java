package com.BlogApplication.Blog.util;

import com.BlogApplication.Blog.models.User;

import java.util.List;

/**
 * The 8 built-in "icon on gradient" avatar options (Bodh Sea's own ocean/nature iconography,
 * not stock characters) - shared by the preset picker and the plain color picker, and the
 * single source of truth for what a valid preset key or gradient looks like. Every gradient a
 * User ends up with is generated here, server-side, from a validated index or hue - the raw
 * CSS string a client could submit is never trusted or stored directly, so th:style can use
 * avatarGradient with no further sanitization.
 */
public class AvatarPresets {

    private AvatarPresets() {
    }

    public record Gradient(String presetKey, String label, String css) {
    }

    public static final List<Gradient> GRADIENTS = List.of(
            new Gradient("wave", "Ocean", "linear-gradient(135deg, #5b8def 0%, #7c5cf5 100%)"),
            new Gradient("leaf", "Tide", "linear-gradient(135deg, #22c1c3 0%, #2563eb 100%)"),
            new Gradient("moon", "Coral Reef", "linear-gradient(135deg, #fa709a 0%, #fee140 100%)"),
            new Gradient("compass", "Seafoam", "linear-gradient(135deg, #43e97b 0%, #2fb8af 100%)"),
            new Gradient("shell", "Dusk", "linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)"),
            new Gradient("anchor", "Golden Hour", "linear-gradient(135deg, #f6d365 0%, #fda085 100%)"),
            new Gradient("lotus", "Deep Current", "linear-gradient(135deg, #16222a 0%, #3a6073 100%)"),
            new Gradient("sun", "Slate Storm", "linear-gradient(135deg, #485563 0%, #29323c 100%)")
    );

    public static boolean isValidPresetKey(String key) {
        return key != null && GRADIENTS.stream().anyMatch(g -> g.presetKey().equals(key));
    }

    public static String gradientForPreset(String key) {
        return GRADIENTS.stream()
                .filter(g -> g.presetKey().equals(key))
                .map(Gradient::css)
                .findFirst()
                .orElse(GRADIENTS.get(0).css());
    }

    public static boolean isValidSwatchIndex(int index) {
        return index >= 0 && index < GRADIENTS.size();
    }

    public static String gradientForSwatch(int index) {
        return GRADIENTS.get(index).css();
    }

    public static String gradientForHue(int hue) {
        int h = ((hue % 360) + 360) % 360;
        int h2 = (h + 45) % 360;
        return "linear-gradient(135deg, hsl(" + h + ",75%,62%) 0%, hsl(" + h2 + ",70%,48%) 100%)";
    }

    // Resolves the "preset"/"color" branches shared by both the registration form and the
    // profile-page editor - "photo" is handled by the caller since it also needs the
    // MultipartFile and ImageStorageService, which don't belong in this class. Returns null for
    // "photo" mode or an invalid/missing selection, meaning "leave the avatar unset."
    public static String resolveGradient(String mode, String preset, Integer swatchIndex, Integer hue) {
        if ("preset".equals(mode)) {
            return isValidPresetKey(preset) ? gradientForPreset(preset) : null;
        }
        if ("color".equals(mode)) {
            if (hue != null) {
                return gradientForHue(hue);
            }
            if (swatchIndex != null && isValidSwatchIndex(swatchIndex)) {
                return gradientForSwatch(swatchIndex);
            }
        }
        return null;
    }

    // Used by every avatar-rendering template so the tri-state (photo / preset / color)
    // background rule lives in exactly one place instead of being repeated as a ternary
    // in postDashboard, viewPostByID, commentNode, and profile.
    public static String gradientStyle(User user) {
        if (user == null || user.getAvatarGradient() == null || user.getAvatarGradient().isBlank()) {
            return "";
        }
        if ("preset".equals(user.getAvatarType()) || "color".equals(user.getAvatarType())) {
            return "background:" + user.getAvatarGradient() + ";";
        }
        return "";
    }
}

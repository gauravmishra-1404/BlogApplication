package com.BlogApplication.Blog.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name="users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private int id;

    @Column(name="user_name")
    private String name;

    @Column(name="email", unique = true, nullable = false)
    private String email;

    @Column(name="role")
    private String role;

    @JsonIgnore
    @Column(name="password")
    private String password;

    // Nullable Boolean, not a primitive - accounts created before this feature existed have
    // no value for this column (ddl-auto=update adds it without backfilling existing rows,
    // same issue we hit with Comment.edited). isEmailVerified() below treats null as "verified"
    // so pre-existing accounts aren't locked out; only new registrations start out false.
    @Column(name = "email_verified")
    private Boolean emailVerified;

    // Pre-existing DB column (shared table, unique constraint already in place) that this app
    // never mapped until the profile page - the @handle in /profile/{username} and the URL
    // itself. Existing production rows have this as null; UserServiceImpl backfills/generates
    // one for every account going forward.
    @Column(name = "username", unique = true)
    private String username;

    // Holds the new address while an email change is awaiting confirmation - `email` (the
    // actual login credential) only ever gets overwritten once the confirmation link is
    // clicked, so a typo'd new address can never lock someone out of their own account. Null
    // the rest of the time (no change in flight).
    @Column(name = "pending_email")
    private String pendingEmail;

    @Column(name = "mobile_number")
    private String mobileNumber;

    // Unlike emailVerified, null here means NOT verified - there's no legacy-row case to be
    // charitable about (every account predates this column, and a null/absent mobile number
    // was never verified in the first place).
    @Column(name = "mobile_verified")
    private Boolean mobileVerified;

    @Column(name = "pending_mobile_number")
    private String pendingMobileNumber;

    // OTP state kept directly on the row rather than a new table - unlike VerificationToken
    // (a long unguessable link token, reused across VERIFY_EMAIL/RESET_PASSWORD/CHANGE_EMAIL),
    // a mobile OTP is a short numeric code with a single always-current purpose per user, so a
    // dedicated table would just be an unused generality.
    @Column(name = "mobile_otp_code")
    private String mobileOtpCode;

    @Column(name = "mobile_otp_expiry")
    private LocalDateTime mobileOtpExpiry;

    @Column(name = "bio")
    private String bio;

    // URL string (e.g. a Cloudinary-hosted link), not the image bytes - profile picture
    // rendering falls back to the initial-letter avatar wherever this is null.
    @Column(name = "profile_pic")
    private String profilePic;

    // Which of the 3 avatar sources is currently active: "photo" (profilePic), "preset"
    // (avatarPreset icon on avatarGradient), or "color" (initials on avatarGradient). Null
    // means the legacy default - show profilePic if set, else the initial letter on the
    // page's default gradient - so existing rows need no backfill. See AvatarPresets.
    @Column(name = "avatar_type")
    private String avatarType;

    // Icon key (e.g. "wave") when avatarType = "preset" - looked up as SVG symbol "#i-av-{key}".
    @Column(name = "avatar_preset")
    private String avatarPreset;

    // CSS gradient value, always generated server-side (AvatarPresets) from a validated swatch
    // index or hue - never stored verbatim from client input - so it's safe to drop straight
    // into a th:style attribute with no further sanitization.
    @Column(name = "avatar_gradient")
    private String avatarGradient;

    // Same tri-state model as the avatar fields above, applied to the profile banner instead -
    // "photo" (coverImage URL), "preset" (a named Bodh Sea scene, "#cover-{key}"), or "color"
    // (coverGradient alone). Null means the default gradient banner. See CoverPresets.
    @Column(name = "cover_image")
    private String coverImage;

    @Column(name = "cover_type")
    private String coverType;

    @Column(name = "cover_preset")
    private String coverPreset;

    @Column(name = "cover_gradient")
    private String coverGradient;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    // Bumped automatically on every save via @PreUpdate below - not something a service method
    // has to remember to set (that's exactly the class of gap the username backfill and IDOR
    // bugs both were: a manual step nobody remembered for every code path). Nullable so existing
    // rows in the shared table need no backfill; new rows get it from @PrePersist immediately.
    // DB-level only for now - no UI surfaces this yet.
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = {CascadeType.PERSIST,CascadeType.MERGE}, fetch = FetchType.LAZY)
    private List<Post> posts;

    public List<Post> getPosts() {
        return posts;
    }

    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isEmailVerified() {
        return emailVerified == null || emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPendingEmail() {
        return pendingEmail;
    }

    public void setPendingEmail(String pendingEmail) {
        this.pendingEmail = pendingEmail;
    }

    public String getMobileNumber() {
        return mobileNumber;
    }

    public void setMobileNumber(String mobileNumber) {
        this.mobileNumber = mobileNumber;
    }

    public boolean isMobileVerified() {
        return mobileVerified != null && mobileVerified;
    }

    public void setMobileVerified(boolean mobileVerified) {
        this.mobileVerified = mobileVerified;
    }

    public String getPendingMobileNumber() {
        return pendingMobileNumber;
    }

    public void setPendingMobileNumber(String pendingMobileNumber) {
        this.pendingMobileNumber = pendingMobileNumber;
    }

    public String getMobileOtpCode() {
        return mobileOtpCode;
    }

    public void setMobileOtpCode(String mobileOtpCode) {
        this.mobileOtpCode = mobileOtpCode;
    }

    public LocalDateTime getMobileOtpExpiry() {
        return mobileOtpExpiry;
    }

    public void setMobileOtpExpiry(LocalDateTime mobileOtpExpiry) {
        this.mobileOtpExpiry = mobileOtpExpiry;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getProfilePic() {
        return profilePic;
    }

    public void setProfilePic(String profilePic) {
        this.profilePic = profilePic;
    }

    public String getAvatarType() {
        return avatarType;
    }

    public void setAvatarType(String avatarType) {
        this.avatarType = avatarType;
    }

    public String getAvatarPreset() {
        return avatarPreset;
    }

    public void setAvatarPreset(String avatarPreset) {
        this.avatarPreset = avatarPreset;
    }

    public String getAvatarGradient() {
        return avatarGradient;
    }

    public void setAvatarGradient(String avatarGradient) {
        this.avatarGradient = avatarGradient;
    }

    public String getCoverImage() {
        return coverImage;
    }

    public void setCoverImage(String coverImage) {
        this.coverImage = coverImage;
    }

    public String getCoverType() {
        return coverType;
    }

    public void setCoverType(String coverType) {
        this.coverType = coverType;
    }

    public String getCoverPreset() {
        return coverPreset;
    }

    public void setCoverPreset(String coverPreset) {
        this.coverPreset = coverPreset;
    }

    public String getCoverGradient() {
        return coverGradient;
    }

    public void setCoverGradient(String coverGradient) {
        this.coverGradient = coverGradient;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

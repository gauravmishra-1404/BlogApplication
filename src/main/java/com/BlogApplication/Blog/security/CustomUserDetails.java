package com.BlogApplication.Blog.security;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import com.BlogApplication.Blog.models.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

// Holds a detached snapshot of the fields auth actually needs, not a live User reference -
// deliberately, since this object lives inside the HttpSession, and sessions now live in Redis
// (application.properties, infra/terraform/redis.tf), serialized by Spring Session. The original
// version held the User entity directly, which broke that the moment anyone actually logged in:
// User isn't Serializable, and even fixing that wouldn't have been enough, since it also carries
// a lazy @OneToMany List<Post> posts - a live Hibernate proxy that's a well-known way to end up
// with either a LazyInitializationException or an unserializable proxy object, not something to
// carry into a session at all. Confirmed live: NotSerializableException on the User entity itself,
// caught right after fixing the two earlier Redis startup crashes. Every controller in this app
// already re-fetches a fresh User via userRepo.findByEmail(authentication.getName()) when it
// actually needs one rather than casting the principal - this class was never meant to be a
// substitute for that.
public class CustomUserDetails implements UserDetails, Serializable {

    private final String email;
    private final String password;
    private final String role;
    private final boolean emailVerified;

    public CustomUserDetails(User user) {
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.role = user.getRole();
        // isEmailVerified(), not the raw emailVerified field - preserves the same null-safe
        // "legacy rows default to verified" semantics User.isEmailVerified() already implements,
        // snapshotted into a definite boolean at login time.
        this.emailVerified = user.isEmailVerified();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // Spring Security's DaoAuthenticationProvider checks isEnabled() as part of every login
    // attempt and rejects with DisabledException if false - so an unverified account simply
    // can't authenticate until it clicks the emailed verification link.
    @Override
    public boolean isEnabled() {
        return emailVerified;
    }

    // Spring Security's concurrent-session registry (SessionRegistryImpl) keys sessions by
    // principal.equals()/hashCode(). Without this override each login builds a fresh
    // CustomUserDetails instance and falls back to Object identity, so the registry can never
    // tell that two logins belong to the same account - maximumSessions() would silently do
    // nothing. Keying on the user's email (their login id) fixes that.
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CustomUserDetails)) return false;
        return email.equals(((CustomUserDetails) other).email);
    }

    @Override
    public int hashCode() {
        return email.hashCode();
    }

}

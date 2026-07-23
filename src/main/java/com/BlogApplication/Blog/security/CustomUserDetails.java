package com.BlogApplication.Blog.security;

import java.util.Collection;
import java.util.List;

import com.BlogApplication.Blog.models.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;


public class CustomUserDetails implements UserDetails {

    private User user;

    public CustomUserDetails(User user) {
        super();
        this.user = user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        SimpleGrantedAuthority simpleGrantedAuthority = new SimpleGrantedAuthority(user.getRole());
        return List.of(simpleGrantedAuthority);
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {

        return user.getEmail();
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

        return user.isEmailVerified();
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
        return user.getEmail().equals(((CustomUserDetails) other).user.getEmail());
    }

    @Override
    public int hashCode() {
        return user.getEmail().hashCode();
    }

}

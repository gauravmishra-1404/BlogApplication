package com.BlogApplication.Blog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
public class SecurityConfig {
    @Bean
    public UserDetailsService getUserDetailService() {
        return new UserDetailsServiceImpl();
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider daoAuthenticationProvider = new DaoAuthenticationProvider();
        daoAuthenticationProvider.setUserDetailsService(this.getUserDetailService());
        daoAuthenticationProvider.setPasswordEncoder(passwordEncoder());
        return daoAuthenticationProvider;

    }

    // Tracks who's logged in where, so maximumSessions() below can enforce one active
    // session per account instead of letting the same login work unlimited times at once.
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Without this listener, Spring Security's session registry never finds out when a
    // session actually ends (browser close, timeout) - it would keep counting "ghost"
    // sessions as active forever, and maximumSessions(1) would eventually lock everyone out.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // /h2-console is only ever registered when spring.h2.console.enabled=true (the
                // docker/test profiles) - in production (Postgres, console disabled) this rule
                // is inert since the endpoint doesn't exist. CSRF is ignored here because the H2
                // console's own login form doesn't carry Spring's CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/api/login", "/h2-console/**").permitAll()
                        // Restrictive rules MUST come before the broader permitAll patterns below —
                        // authorizeHttpRequests matches in declaration order and stops at the first hit,
                        // so a broad "/posts/**" listed earlier would silently shadow these.
                        // Exact paths (no trailing "/**") are used wherever the controller mapping has
                        // no further path segments — a trailing "/**" does not reliably match the bare path.
                        .requestMatchers(
                                "/posts/edit",
                                "/post/publish",
                                "/posts/createForm",
                                "/post/republish",
                                "/posts/delete",
                                "/posts/comments/delete/{id}",
                                "/api/posts/createForm",
                                "/api/post/publish",
                                "/api/posts/{id}/edit",
                                "/api/posts/{id}/delete",
                                "/api/posts/comments/{id}/delete"
                        ).hasAnyRole("ADMIN", "AUTHOR")
                        .requestMatchers(
                                "/CSS/**",
                                "/js/**",
                                "/images/**",
                                "/post/viewPost",
                                "/post/download",
                                "/posts",
                                "/posts/filter-author",
                                "/posts/filter-tag",
                                "/posts/search",
                                "/posts/sort",
                                "/registerUser",
                                "/forgot-password",
                                "/reset-password",
                                "/verify-email",
                                "/resend-verification",
                                "/api/posts",
                                "/api/posts/search",
                                "/api/posts/sort",
                                "/api/posts/filter-author",
                                "/api/posts/filter-tag",
                                "/api/post/{id}/view",
                                "/api/users/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/authenticateTheUser")
                        .defaultSuccessUrl("/posts", true)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/posts")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "remember-me")
                        .permitAll()
                )
                // Up to 2 active sessions per account (e.g. phone + laptop). A 3rd concurrent
                // login invalidates the oldest of the two, rather than letting the same login
                // work an unlimited number of times simultaneously.
                .sessionManagement(session -> session
                        .maximumSessions(2)
                        .maxSessionsPreventsLogin(false)
                        .sessionRegistry(sessionRegistry())
                )
                // Every login is remembered for 30 days via a signed cookie (hash-based -
                // no extra DB table, since this Postgres instance is shared with another app
                // and new tables there are avoided unless truly needed). alwaysRemember(true)
                // means this applies automatically - no "remember me" checkbox to opt into.
                .rememberMe(remember -> remember
                        .key("bodhSeaRememberMeKey")
                        .userDetailsService(getUserDetailService())
                        .tokenValiditySeconds(60 * 60 * 24 * 30)
                        .alwaysRemember(true)
                )
        ;

        return http.build();
    }
}
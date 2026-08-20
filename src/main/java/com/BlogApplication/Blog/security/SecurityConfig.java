package com.BlogApplication.Blog.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

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
    //
    // Two implementations, exactly one of which is ever actually created:
    //
    // - redisBackedSessionRegistry, when spring.session.store-type=redis (production - see
    //   application.properties, infra/terraform/redis.tf): reads live session state straight out
    //   of the shared Redis store via FindByIndexNameSessionRepository, so "who's logged in
    //   where" is correct no matter which of the (up to 4) Beanstalk instances actually served
    //   each request. This is the whole point of adding Redis in the first place - without it,
    //   swapping the session STORE to Redis wouldn't have been enough on its own, since this
    //   registry bean is what maximumSessions() actually asks "how many sessions does this user
    //   have right now", and a plain SessionRegistryImpl only ever knows about sessions THIS one
    //   JVM has personally seen.
    // - inMemorySessionRegistry, the @ConditionalOnMissingBean fallback: used whenever the Redis
    //   bean above didn't get created (local `docker` profile, tests - spring.session.store-type
    //   defaults to "none" there), so local dev never needs a real Redis instance running.
    @Bean
    @ConditionalOnProperty(prefix = "spring.session", name = "store-type", havingValue = "redis")
    public SessionRegistry redisBackedSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    @Bean
    @ConditionalOnMissingBean(SessionRegistry.class)
    public SessionRegistry inMemorySessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Without this listener, Spring Security's session registry never finds out when a
    // session actually ends (browser close, timeout) - it would keep counting "ghost"
    // sessions as active forever, and maximumSessions(1) would eventually lock everyone out.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    // SessionRegistry injected as a parameter rather than called as a method - there's no longer
    // one single sessionRegistry() bean method to call directly (see the two conditional ones
    // above), so this just asks Spring for whichever ONE of them actually got created.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
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
                        // Everything that's actually part of the app - dashboard, posts, profiles,
                        // search/filter/sort, downloads, the JSON API mirrors - requires login.
                        // The only permitAll list left below is the small, unavoidable set of
                        // routes needed to LOG IN or CREATE an account in the first place: you
                        // can't gate the login page behind being logged in.
                        .requestMatchers(
                                "/CSS/**",
                                "/js/**",
                                "/images/**",
                                "/registerUser",
                                "/forgot-password",
                                "/reset-password",
                                "/verify-email",
                                "/resend-verification",
                                "/confirm-email-change",
                                "/api/users/register"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/authenticateTheUser")
                        .defaultSuccessUrl("/home", true)
                )
                // logoutSuccessUrl deliberately points at /login, not /home - .permitAll() here
                // auto-whitelists whatever that URL is (so /logout itself works for a session
                // that's already gone), and pointing it at /home would silently punch that path
                // back open to anonymous users, undoing the lockdown above.
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
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
                        .sessionRegistry(sessionRegistry)
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
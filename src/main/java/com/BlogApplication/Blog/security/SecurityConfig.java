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

    // Tracks who's logged in where, so maximumSessions() below can enforce 2 active sessions per
    // account instead of letting the same login work unlimited times at once.
    //
    // Deliberately plain SessionRegistryImpl even now that sessions live in shared Redis
    // (application.properties, infra/terraform/redis.tf) - a SpringSessionBackedSessionRegistry
    // (reading live state from Redis so the 2-session limit is exactly accurate across every
    // Beanstalk instance) was tried and reverted: it requires Spring Session's INDEXED Redis
    // repository, which runs `CONFIG SET notify-keyspace-events` on startup to detect session
    // expiry - and AWS ElastiCache rejects the CONFIG command outright for any client ("ERR
    // unknown command 'CONFIG'"), confirmed live. That crashed the app on every boot (two real
    // outages before this was reverted).
    //
    // What staying with SessionRegistryImpl actually costs: this bean only ever knows about
    // sessions THIS one JVM has personally seen, so in a multi-instance deployment the 2-session
    // limit becomes approximate per-instance rather than exact account-wide - someone COULD end
    // up with slightly more than 2 total concurrent sessions across different instances. That's
    // a minor abuse-limit leniency, not a correctness bug - it can never cause anyone to be
    // wrongly logged out, which was the actual, critical problem Redis-backed sessions exist to
    // fix. That fix is untouched by this: HttpSession data itself is still fully shared via
    // Redis (spring-session-data-redis's default, non-indexed RedisSessionRepository - no CONFIG
    // command involved at all), so a request landing on a different instance than the one that
    // logged someone in still finds their session correctly. Revisit with the indexed repository
    // later (needs notify-keyspace-events set via the ElastiCache parameter group instead of at
    // runtime, plus a ConfigureRedisAction.NO_OP bean to stop Spring Session from attempting the
    // blocked CONFIG call itself) only if exact cross-instance enforcement ever actually matters
    // enough to justify that added complexity.
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
    public SecurityFilterChain filterChain(HttpSecurity http, SessionRegistry sessionRegistry) throws Exception {
        http
                // /h2-console is only ever registered when spring.h2.console.enabled=true (the
                // docker/test profiles) - in production (Postgres, console disabled) this rule
                // is inert since the endpoint doesn't exist. CSRF is ignored here because the H2
                // console's own login form doesn't carry Spring's CSRF token.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/api/login", "/h2-console/**").permitAll()
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
                        // The ALB's own health check (infra/terraform/beanstalk.tf) - separate
                        // from the login/account-creation list below since it's not part of that
                        // flow at all, just infrastructure plumbing that also can't require auth.
                        .requestMatchers("/healthz").permitAll()
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
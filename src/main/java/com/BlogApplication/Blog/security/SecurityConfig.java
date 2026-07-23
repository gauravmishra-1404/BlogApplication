package com.BlogApplication.Blog.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/api/login").permitAll()
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
                                "/post/viewPost",
                                "/posts",
                                "/posts/filter-author",
                                "/posts/filter-tag",
                                "/posts/search",
                                "/posts/sort",
                                "/registerUser",
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
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
        ;

        return http.build();
    }
}
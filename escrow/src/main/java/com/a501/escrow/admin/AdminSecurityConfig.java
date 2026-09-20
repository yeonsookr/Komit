package com.a501.escrow.admin;

import com.a501.escrow.config.AppProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class AdminSecurityConfig {

    @Bean
    UserDetailsService adminUsers(
            @Value("${app.admin.username}") String username,
            @Value("${app.admin.password-hash}") String passwordHash
    ) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordHash)
                .roles("ESCROW_ADMIN")
                .build());
    }

    @Bean
    @Order(1)
    SecurityFilterChain adminSecurity(HttpSecurity http, AppProperties properties) throws Exception {
        String externalPrefix = normalizedPath(properties.publicBaseUrl().getPath());
        return http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ESCROW_ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .successHandler((request, response, authentication) ->
                                response.sendRedirect(externalPrefix + "/admin"))
                        .failureHandler((request, response, exception) ->
                                response.sendRedirect(externalPrefix + "/admin/login?error"))
                        .permitAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) ->
                                response.sendRedirect(externalPrefix + "/admin/login")))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.sendRedirect(externalPrefix + "/admin/login?logout"))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID", "ESCROWSESSION"))
                .headers(headers -> headers.cacheControl(cache -> {}))
                .build();
    }

    private static String normalizedPath(String path) {
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "";
        }
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    @Bean
    @Order(2)
    SecurityFilterChain applicationSecurity(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .build();
    }
}

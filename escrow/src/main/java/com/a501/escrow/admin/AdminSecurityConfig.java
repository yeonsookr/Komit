package com.a501.escrow.admin;

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
    SecurityFilterChain adminSecurity(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/admin/**")
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ESCROW_ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/admin/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers.cacheControl(cache -> {}))
                .build();
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

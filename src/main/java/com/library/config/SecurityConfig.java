package com.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/register", "/static/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .loginProcessingUrl("/login")
                .failureHandler((request, response, exception) -> {
                    if (exception instanceof org.springframework.security.authentication.LockedException) {
                        response.sendRedirect(request.getContextPath() + "/login?locked");
                    } else {
                        response.sendRedirect(request.getContextPath() + "/login?error");
                    }
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .permitAll()
            );

        return http.build();
    }

    @Bean
    public org.springframework.security.authentication.AuthenticationProvider authenticationProvider(
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        org.springframework.security.authentication.dao.DaoAuthenticationProvider provider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider() {
            @Override
            public org.springframework.security.core.Authentication authenticate(org.springframework.security.core.Authentication authentication) throws org.springframework.security.core.AuthenticationException {
                try {
                    return super.authenticate(authentication);
                } catch (org.springframework.security.authentication.LockedException e) {
                    String username = (authentication.getPrincipal() == null) ? "NONE_PROVIDED" : authentication.getName();
                    String password = (String) authentication.getCredentials();
                    try {
                        org.springframework.security.core.userdetails.UserDetails user = this.getUserDetailsService().loadUserByUsername(username);
                        if (!this.getPasswordEncoder().matches(password, user.getPassword())) {
                            throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
                        }
                    } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
                        throw new org.springframework.security.authentication.BadCredentialsException("Bad credentials");
                    }
                    throw e; // Password is correct, so keep the LockedException
                }
            }
        };
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

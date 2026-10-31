package org.example.revoenglishwebsite.config;

import org.springframework.context.annotation.Bean;
import org.example.revoenglishwebsite.account.PortalLoginSuccessHandler;
import org.example.revoenglishwebsite.account.Role;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy;

@Configuration
public class SecurityConfig {

    /**
     * Everything is served from this origin (fonts are self-hosted, no third-party scripts),
     * so the policy can stay strict. Loosen it deliberately if an analytics provider is added.
     */
    static final String CONTENT_SECURITY_POLICY = String.join("; ",
            "default-src 'self'",
            "script-src 'self'",
            "style-src 'self'",
            "img-src 'self' data:",
            "font-src 'self'",
            "connect-src 'self'",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'");

    static final String PERMISSIONS_POLICY =
            "camera=(), microphone=(), geolocation=(), payment=(), usb=()";

    /**
     * BCrypt via the delegating encoder: hashes are stored as "{bcrypt}…", which allows switching the
     * algorithm later without invalidating existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // The marketing site is public. Only the student portal is protected — on the server, by role:
    //   /admin/**     administrators only (account management)
    //   /dashboard/** students only
    //   /account/**   any signed-in user (own password)
    // Everything else stays public so unknown URLs still get the normal 404 page.
    // CSRF protection is on (Spring Security default) for every form, including login and logout.
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin", "/admin/**").hasRole(Role.ADMIN.name())
                        .requestMatchers("/dashboard", "/dashboard/**").hasRole(Role.STUDENT.name())
                        .requestMatchers("/account", "/account/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginPage("/login")
                        .usernameParameter("login")
                        .passwordParameter("password")
                        .successHandler(new PortalLoginSuccessHandler())
                        // Same message for unknown login, wrong password and disabled account
                        .failureUrl("/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID"))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY))
                        .crossOriginOpenerPolicy(coop -> coop.policy(CrossOriginOpenerPolicy.SAME_ORIGIN)))
                .build();
    }
}

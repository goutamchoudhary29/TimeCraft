package com.timecraft.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.timecraft.audit.service.AuditLogService;
import com.timecraft.authentication.entity.AccountStatus;
import com.timecraft.authentication.entity.RoleName;
import com.timecraft.authentication.repository.UserRepository;
import com.timecraft.common.util.RequestUtils;
import com.timecraft.config.AppProperties;
import com.timecraft.exception.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Strict dual security filter chain configuration:
 *
 * 1. Chain 1 (@Order(1)) matches /admin/** and /api/v1/admin/**.
 *    - Stateful HTTP session authentication (form login at /admin/login).
 *    - CSRF protection ENABLED for all state-changing requests.
 *    - JwtAuthenticationFilter is NOT attached to this chain, so a customer JWT
 *      Bearer token can never authenticate against any admin route or admin API.
 *    - Requires ROLE_ADMIN.
 *
 * 2. Chain 2 (@Order(2)) matches /api/v1/** (excluding /api/v1/admin/**).
 *    - Stateless JWT authentication via JwtAuthenticationFilter.
 *    - Customer-owned endpoints require ROLE_CUSTOMER.
 *
 * 3. Chain 3 (@Order(3)) serves public storefront assets, Swagger UI and Actuator health.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final TimeCraftUserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain adminFilterChain(HttpSecurity http) throws Exception {
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepo.setCookiePath("/");

        http.securityMatcher(new OrRequestMatcher(
                        new AntPathRequestMatcher("/admin/**"),
                        new AntPathRequestMatcher("/api/v1/admin/**")
                ))
                .authenticationProvider(authenticationProvider())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepo))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login", "/admin/access-denied").permitAll()
                        .requestMatchers("/admin/**", "/api/v1/admin/**").hasRole(RoleName.ADMIN.name())
                )
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .loginProcessingUrl("/admin/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((request, response, authentication) -> {
                            TimeCraftPrincipal principal = (TimeCraftPrincipal) authentication.getPrincipal();
                            if (!principal.hasRole(RoleName.ADMIN)) {
                                request.getSession().invalidate();
                                auditLogService.log(principal.getId(), principal.getEmail(),
                                        "ADMIN_LOGIN_DENIED", "USER", String.valueOf(principal.getId()),
                                        "Non-admin attempted admin login", RequestUtils.clientIp(request));
                                response.sendRedirect("/admin/access-denied");
                                return;
                            }
                            userRepository.findById(principal.getId()).ifPresent(u -> {
                                u.setFailedLoginAttempts(0);
                                u.setLockedUntil(null);
                                u.setLastLoginAt(Instant.now());
                                userRepository.save(u);
                            });
                            auditLogService.log(principal.getId(), principal.getEmail(),
                                    "ADMIN_LOGIN", "USER", String.valueOf(principal.getId()),
                                    "Admin logged in to dashboard", RequestUtils.clientIp(request));
                            response.sendRedirect("/admin/dashboard");
                        })
                        .failureHandler((request, response, exception) -> {
                            String email = request.getParameter("email");
                            if (email != null) {
                                userRepository.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
                                    int attempts = u.getFailedLoginAttempts() + 1;
                                    u.setFailedLoginAttempts(attempts);
                                    if (attempts >= 5) {
                                        u.setLockedUntil(Instant.now().plus(Duration.ofMinutes(15)));
                                    }
                                    userRepository.save(u);
                                });
                            }
                            auditLogService.log(null, email, "ADMIN_LOGIN_FAILED", "USER", null,
                                    "Admin login failed: " + exception.getMessage(), RequestUtils.clientIp(request));
                            response.sendRedirect("/admin/login?error");
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/admin/logout", "POST"))
                        .addLogoutHandler((request, response, authentication) -> {
                            if (authentication != null && authentication.getPrincipal() instanceof TimeCraftPrincipal p) {
                                auditLogService.log(p.getId(), p.getEmail(), "ADMIN_LOGOUT", "USER",
                                        String.valueOf(p.getId()), "Admin logged out", RequestUtils.clientIp(request));
                            }
                        })
                        .invalidateHttpSession(true)
                        .deleteCookies("TC_ADMIN_SESSION")
                        .logoutSuccessUrl("/admin/login?logout")
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/v1/admin/")) {
                                writeJsonError(request, response, HttpStatus.UNAUTHORIZED, "ADMIN_AUTH_REQUIRED",
                                        "Admin session authentication is required");
                            } else {
                                response.sendRedirect("/admin/login");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().startsWith("/api/v1/admin/")) {
                                writeJsonError(request, response, HttpStatus.FORBIDDEN, "ADMIN_FORBIDDEN",
                                        "Admin role and a valid CSRF token are required");
                            } else {
                                response.sendRedirect("/admin/access-denied");
                            }
                        })
                )
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain customerApiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/**")
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/verify-otp",
                                "/api/v1/auth/resend-otp",
                                "/api/v1/auth/login",
                                "/api/v1/auth/google",
                                "/api/v1/auth/google-config",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/verify-email",
                                "/api/v1/auth/resend-verification",
                                "/api/v1/auth/forgot-password",
                                "/api/v1/auth/reset-password"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/products/**",
                                "/api/v1/brands/**",
                                "/api/v1/categories/**",
                                "/api/v1/store/**",
                                "/api/v1/reviews/featured",
                                "/api/v1/shipments/track/**"
                        ).permitAll()
                        .requestMatchers("/api/v1/cart/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/payments/webhook",
                                "/api/v1/support/contact",
                                "/api/v1/store/newsletter"
                        ).permitAll()
                        .anyRequest().hasRole(RoleName.CUSTOMER.name())
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJsonError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                                        "Please sign in to continue"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJsonError(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN",
                                        "You do not have permission to access this resource"))
                )
                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain publicWebFilterChain(HttpSecurity http) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole(RoleName.ADMIN.name())
                        .anyRequest().permitAll()
                );
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Guest-Token", "X-Idempotency-Key", "X-XSRF-TOKEN"));
        config.setExposedHeaders(List.of("X-Guest-Token"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    private void writeJsonError(HttpServletRequest request, HttpServletResponse response,
                                HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, request.getRequestURI(), null);
        objectMapper.writeValue(response.getWriter(), error);
    }
}

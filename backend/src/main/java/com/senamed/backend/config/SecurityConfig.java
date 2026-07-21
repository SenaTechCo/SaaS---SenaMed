package com.senamed.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senamed.backend.security.JwtAuthenticationFilter;
import com.senamed.backend.security.JwtService;
import com.senamed.backend.security.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;
    private final RestAccessDeniedHandler restAccessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Value("${senamed.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${senamed.rate-limit.public-appointments.capacity:8}")
    private int rateLimitCapacity;

    @Value("${senamed.rate-limit.public-appointments.window-seconds:60}")
    private int rateLimitWindowSeconds;

    public SecurityConfig(
            JwtService jwtService,
            RestAuthenticationEntryPoint restAuthenticationEntryPoint,
            RestAccessDeniedHandler restAccessDeniedHandler,
            ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
        this.restAccessDeniedHandler = restAccessDeniedHandler;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(restAuthenticationEntryPoint)
                        .accessDeniedHandler(restAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers("/api/webhooks/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/doctors/me/**").authenticated()
                        .requestMatchers("/api/users/me/**").authenticated()
                        .requestMatchers("/api/doctors/**").hasRole("ADMIN")
                        .requestMatchers("/api/patients/**").hasRole("ADMIN")
                        .requestMatchers("/api/services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/clinics/me").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/subscriptions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/appointments").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/appointments/*/cancel").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/*/atender").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/appointments/*").hasRole("ADMIN")
                        .requestMatchers("/api/finance/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        new RateLimitFilter(objectMapper, rateLimitCapacity, Duration.ofSeconds(rateLimitWindowSeconds)),
                        JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}

package com.streampay.authentication.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.authentication.dto.ErrorResponseDto;
import com.streampay.authentication.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.LocalDateTime;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        httpSecurity
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh",
                                "/api/v1/auth/logout"
                        ).permitAll()
                        .requestMatchers("/api/v1/auth/customer")
                        .hasRole("CUSTOMER")

                        .requestMatchers("/api/v1/auth/merchant")
                        .hasRole("MERCHANT")

                        .requestMatchers("/api/v1/auth/admin")
                        .hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception

                        .authenticationEntryPoint(
                                (request, response, authException) -> {

                                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                    response.setContentType("application/json");

                                    ErrorResponseDto errorResponse = new ErrorResponseDto(
                                            LocalDateTime.now(),
                                            401,
                                            "Unauthorized",
                                            "Authentication required or token is invalid"
                                    );
                                    objectMapper.writeValue(response.getOutputStream(), errorResponse);
                                }
                        )

                        .accessDeniedHandler(
                                (request, response, accessDeniedException) -> {

                                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                                    response.setContentType("application/json");

                                    ErrorResponseDto errorResponse = new ErrorResponseDto(
                                            LocalDateTime.now(),
                                            403,
                                            "Forbidden",
                                            "You do not have permission to access this resource"
                                    );

                                    objectMapper.writeValue(response.getOutputStream(), errorResponse);
                                }
                        )
                )
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return httpSecurity.build();
    }
}

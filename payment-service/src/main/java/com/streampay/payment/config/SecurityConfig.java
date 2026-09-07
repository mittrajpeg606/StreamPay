package com.streampay.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.dto.ErrorResponseDto;
import com.streampay.payment.filter.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments")
                        .hasRole("CUSTOMER")
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

        return http.build();
    }
}
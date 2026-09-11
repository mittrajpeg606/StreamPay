package com.streampay.authentication.filter;


import com.streampay.authentication.dto.AuthenticatedUser;
import com.streampay.authentication.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No JWT provided → continue request
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.extractClaims(token);



            String email = claims.getSubject();
            String role = claims.get("role", String.class);
            String merchantId= claims.get("merchantId",String.class);
            String tokenType = claims.get("tokenType", String.class);
            UUID userId=UUID.fromString(claims.get("userId", String.class));

            String id=userId.toString();

//            System.out.println("EMAIL = " + email);
//            System.out.println("TOKEN TYPE = " + tokenType);
//            System.out.println("ROLE = " + role);

            // Only ACCESS tokens can authenticate API requests
            if (!"access".equals(tokenType)) {
                filterChain.doFilter(request, response);
                return;
            }

            if (email != null && role != null &&
                    id !=null &&  (!"ROLE_MERCHANT".equals(role) || merchantId != null) && SecurityContextHolder.getContext().getAuthentication() == null) {

                AuthenticatedUser authenticatedUser = new AuthenticatedUser(email,merchantId,userId, role);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of(new SimpleGrantedAuthority(role))
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

//                System.out.println(
//                        "AUTHORITIES = " +
//                                SecurityContextHolder.getContext()
//                                        .getAuthentication()
//                                        .getAuthorities()
//                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
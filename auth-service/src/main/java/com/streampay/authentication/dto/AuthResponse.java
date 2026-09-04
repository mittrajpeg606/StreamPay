package com.streampay.authentication.dto;


public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
}
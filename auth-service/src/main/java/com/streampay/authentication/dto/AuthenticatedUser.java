package com.streampay.authentication.dto;

import java.util.UUID;

public record AuthenticatedUser(
        String email,
        String merchantId,
        UUID userId,
        String role
) {
}
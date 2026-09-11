package com.streampay.payment.dto;

import java.util.UUID;

public record AuthenticatedUser(
        String email,
        UUID userId,
        String merchantId,
        String role
) {}
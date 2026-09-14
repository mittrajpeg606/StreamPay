package com.streampay.payment.idempotency;

public record IdempotencyDto(
        String requestHash,
        String status,
        String paymentReference,
        String response
) {
}

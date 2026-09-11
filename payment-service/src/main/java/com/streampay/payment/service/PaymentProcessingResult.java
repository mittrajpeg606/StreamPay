package com.streampay.payment.service;

public record PaymentProcessingResult(
        String paymentReference,
        boolean success,
        String reason
) {
}
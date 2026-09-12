package com.streampay.payment.kafka.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentFailedEvent (
    String paymentReference,
    String orderId,
    String customerId,
    String merchantId,
    BigDecimal amount,
    String currency,
    String reason,
    LocalDateTime occuredAt,
    String eventType
) implements PaymentEvent{}


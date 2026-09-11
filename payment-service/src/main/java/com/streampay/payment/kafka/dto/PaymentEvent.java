package com.streampay.payment.kafka.dto;

import java.time.LocalDateTime;

public interface PaymentEvent {

    String paymentReference();

    LocalDateTime occuredAt();
}
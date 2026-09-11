package com.streampay.payment.service;

import com.streampay.payment.kafka.dto.PaymentProcessingEvent;

public interface PaymentProcessor {

    PaymentProcessingResult process(PaymentProcessingEvent event);
}
package com.streampay.payment.dto;

import com.streampay.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResponse(UUID id,
                              String paymentReference,
                              String orderId,
                              String customerId,
                              String merchantId,
                              BigDecimal amount,
                              String currency,
                              PaymentStatus status
) {

}

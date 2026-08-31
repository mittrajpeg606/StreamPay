package com.streampay.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;


public record CreatePaymentRequest( @NotBlank
                                    String orderId,
                                    @NotBlank
                                    String customerId,
                                    @NotBlank
                                    String merchantId,
                                    @NotNull
                                    @DecimalMin(value="0.01")
                                    BigDecimal amount,
                                    @NotBlank
                                    String currency) {

}

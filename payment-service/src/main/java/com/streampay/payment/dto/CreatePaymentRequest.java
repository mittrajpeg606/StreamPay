package com.streampay.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

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
                                    @Size(min = 3, max = 3)
                                    String currency) {

}

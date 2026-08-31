package com.streampay.payment.controller;

import com.streampay.payment.Service.PaymentService;
import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @Valid @RequestBody CreatePaymentRequest request) {

        return paymentService.createPayment(request);
    }

    @GetMapping("/{paymentReference}")
    public PaymentResponse getPayment(
            @PathVariable String paymentReference) {

        return paymentService.getPayment(paymentReference);
    }
}
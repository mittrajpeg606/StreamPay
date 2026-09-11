package com.streampay.payment.controller;

import com.streampay.payment.kafka.PaymentEventProducer;
import com.streampay.payment.kafka.dto.PaymentCreatedEvent;
import com.streampay.payment.kafka.dto.PaymentEvent;
import com.streampay.payment.service.PaymentService;
import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentEventProducer paymentEventProducer;

    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment( @Valid @RequestBody CreatePaymentRequest request, Authentication authentication) {

        String customerEmail=authentication.getName();

        return paymentService.createPayment(request,customerEmail);
    }

    @GetMapping("/{paymentReference}")
    public PaymentResponse getPayment( @PathVariable String paymentReference,Authentication authentication) {

        String customerEmail=authentication.getName();
        return paymentService.getPayment(paymentReference,customerEmail);
    }

    @PostMapping("/{paymentReference}/refund")
    public ResponseEntity<String> refundPayment(@PathVariable String paymentReference,Authentication authentication) {

       return ResponseEntity.ok().body("Refunded the amount");
    }
    @PostMapping("/test-event")
    public ResponseEntity<PaymentEvent> testKafkaProducer(@RequestBody PaymentCreatedEvent paymentCreatedEvent)
    {

        return ResponseEntity.status(HttpStatus.OK).body(paymentEventProducer.sendPaymentEvent(paymentCreatedEvent));

    }
}
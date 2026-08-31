package com.streampay.payment.Service;

import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;
import com.streampay.payment.entities.Payment;
import com.streampay.payment.enums.PaymentStatus;
import com.streampay.payment.exception.PaymentNotFoundException;
import com.streampay.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository)
    {
        this.paymentRepository=paymentRepository;
    }

    public PaymentResponse createPayment(CreatePaymentRequest createPaymentRequest)
    {
        Payment payment= Payment.builder().paymentReference(getPaymentReference()).
                                           customerId(createPaymentRequest.customerId()).
                                           orderId(createPaymentRequest.orderId()).
                                           merchantId(createPaymentRequest.merchantId()).
                                           amount(createPaymentRequest.amount()).
                                           currency(createPaymentRequest.currency()).
                                           createdAt(LocalDateTime.now()).
                                           updatedAt(LocalDateTime.now()).
                                           status(PaymentStatus.CREATED).build();

        return toResponse(paymentRepository.save(payment));
    }


    public PaymentResponse getPayment(String paymentReference){

        Payment payment=paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(()->new PaymentNotFoundException("Payment Not Found"));

        return toResponse(payment);

    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(),
                payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus());
    }

    private String getPaymentReference() {
        return "PAY-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }




}

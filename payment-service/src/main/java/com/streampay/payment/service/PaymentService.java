package com.streampay.payment.service;

import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;
import com.streampay.payment.entities.Payment;
import com.streampay.payment.enums.PaymentStatus;
import com.streampay.payment.exception.InvalidPaymentStateException;
import com.streampay.payment.exception.PaymentNotFoundException;
import com.streampay.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository)
    {
        this.paymentRepository=paymentRepository;
    }

    public PaymentResponse createPayment(CreatePaymentRequest createPaymentRequest,String customerEmail)
    {
        Payment payment= Payment.builder().paymentReference(getPaymentReference()).
                                           customerId(createPaymentRequest.customerId()).
                                           orderId(createPaymentRequest.orderId()).
                                           merchantId(createPaymentRequest.merchantId()).
                                           amount(createPaymentRequest.amount()).
                                           currency(createPaymentRequest.currency()).
                                           createdAt(LocalDateTime.now()).
                                           updatedAt(LocalDateTime.now()).
                                           customerEmail(customerEmail).
                                           status(PaymentStatus.CREATED).build();

        return toResponse(paymentRepository.save(payment));
    }


    public PaymentResponse getPayment(String paymentReference,String customerEmail){

        Payment payment=paymentRepository.findByPaymentReferenceAndCustomerEmail(paymentReference,customerEmail)
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
                payment.getStatus(),
                payment.getCustomerEmail());
    }

    private String getPaymentReference() {
        return "PAY-" +
                UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();
    }


    private void updateStatus( Payment payment, PaymentStatus newStatus) {
        PaymentStatus currentStatus = payment.getStatus();

        boolean validTransition = switch (currentStatus) {

            case CREATED ->
                    newStatus == PaymentStatus.PROCESSING;

            case PROCESSING ->
                    newStatus == PaymentStatus.SUCCESS
                            || newStatus == PaymentStatus.FAILED;

            case SUCCESS ->
                    newStatus == PaymentStatus.REFUNDED;

            case FAILED, REFUNDED ->
                    false;
        };

        if (!validTransition) {
            throw new InvalidPaymentStateException("Invalid payment status transition: " + currentStatus + " -> " + newStatus);
        }

        payment.setStatus(newStatus);
        payment.setUpdatedAt(LocalDateTime.now());
    }



}

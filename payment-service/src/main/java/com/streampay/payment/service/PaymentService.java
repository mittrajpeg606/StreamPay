package com.streampay.payment.service;

import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;
import com.streampay.payment.entities.Payment;
import com.streampay.payment.enums.PaymentStatus;
import com.streampay.payment.exception.InvalidPaymentStateException;
import com.streampay.payment.exception.PaymentNotFoundException;
import com.streampay.payment.kafka.PaymentEventProducer;
import com.streampay.payment.kafka.dto.PaymentCreatedEvent;
import com.streampay.payment.kafka.dto.PaymentFailedEvent;
import com.streampay.payment.kafka.dto.PaymentProcessingEvent;
import com.streampay.payment.kafka.dto.PaymentSuccessEvent;
import com.streampay.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    private final PaymentEventProducer paymentEventProducer;

    private final PaymentProcessor paymentProcessor;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventProducer paymentEventProducer, PaymentProcessor paymentProcessor)
    {
        this.paymentRepository=paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.paymentProcessor = paymentProcessor;
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


        PaymentCreatedEvent event=new PaymentCreatedEvent(payment.getPaymentReference(),
                                                payment.getOrderId(),
                                                payment.getCustomerId(),payment.getMerchantId(),
                                                payment.getAmount(),payment.getCurrency(),payment.getCreatedAt());

        paymentEventProducer.sendPaymentEvent(event);

        return toResponse(paymentRepository.save(payment));
    }


    public PaymentResponse getPayment(String paymentReference,String customerEmail){

        Payment payment=paymentRepository.findByPaymentReferenceAndCustomerEmail(paymentReference,customerEmail)
                .orElseThrow(()->new PaymentNotFoundException("Payment Not Found"));

        return toResponse(payment);

    }

    public void processPayment(String paymentReference) {

        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException("Payment Not Found"));

        updateStatus(payment, PaymentStatus.PROCESSING);

        paymentRepository.save(payment);

        PaymentProcessingEvent event=new PaymentProcessingEvent(payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getCustomerId(),payment.getMerchantId(),
                payment.getAmount(),payment.getCurrency(),"processing",payment.getCreatedAt());

        paymentEventProducer.sendPaymentEvent(event);
    }

    public void handlePaymentProcessing(PaymentProcessingEvent event) {

        PaymentProcessingResult result = paymentProcessor.process(event);

        if (result.success()) {

            PaymentSuccessEvent successEvent = new PaymentSuccessEvent(
                    event.paymentReference(),
                    event.orderId(),
                    event.customerId(),
                    event.merchantId(),
                    event.amount(),
                    event.currency(),
                    event.reason(),
                    LocalDateTime.now()
            );

            paymentEventProducer.sendPaymentEvent(successEvent);

        } else {

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    event.paymentReference(),
                    event.orderId(),
                    event.customerId(),
                    event.merchantId(),
                    event.amount(),
                    event.currency(),
                    result.reason(),
                    LocalDateTime.now()
            );

            paymentEventProducer.sendPaymentEvent(failedEvent);
        }
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


    public void markPaymentSuccess(String paymentReference) {

        Payment payment = paymentRepository
                .findByPaymentReference(paymentReference)
                .orElseThrow(() ->
                        new PaymentNotFoundException("Payment Not Found")
                );

        updateStatus(payment, PaymentStatus.SUCCESS);

        paymentRepository.save(payment);
    }


    public void markPaymentFailed(String paymentReference) {

        Payment payment = paymentRepository
                .findByPaymentReference(paymentReference)
                .orElseThrow(() ->
                        new PaymentNotFoundException("Payment Not Found")
                );

        updateStatus(payment, PaymentStatus.FAILED);

        paymentRepository.save(payment);
    }
}

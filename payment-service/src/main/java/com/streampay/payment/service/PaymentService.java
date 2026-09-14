package com.streampay.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.dto.CreatePaymentRequest;
import com.streampay.payment.dto.PaymentResponse;
import com.streampay.payment.entities.Payment;
import com.streampay.payment.enums.PaymentStatus;
import com.streampay.payment.exception.IdempotencyException;
import com.streampay.payment.exception.InvalidPaymentStateException;
import com.streampay.payment.exception.PaymentAccessDeniedException;
import com.streampay.payment.exception.PaymentNotFoundException;
import com.streampay.payment.idempotency.IdempotencyDto;
import com.streampay.payment.idempotency.IdempotencyService;
import com.streampay.payment.kafka.PaymentEventProducer;
import com.streampay.payment.kafka.dto.*;
import com.streampay.payment.outbox.OutboxEvent;
import com.streampay.payment.outbox.OutboxRepository;
import com.streampay.payment.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private final static String CACHE_KEY="payment:";

    private final PaymentRepository paymentRepository;

    private final PaymentEventProducer paymentEventProducer;

    private final PaymentProcessor paymentProcessor;

    private final ObjectMapper objectMapper;

    private final OutboxRepository outboxRepository;

    private final PaymentCacheService paymentCacheService;

    private final IdempotencyService idempotencyService;

    public PaymentService(PaymentRepository paymentRepository, PaymentEventProducer paymentEventProducer, PaymentProcessor paymentProcessor, ObjectMapper objectMapper, OutboxRepository outboxRepository, PaymentCacheService paymentCacheService, IdempotencyService idempotencyService)
    {
        this.paymentRepository=paymentRepository;
        this.paymentEventProducer = paymentEventProducer;
        this.paymentProcessor = paymentProcessor;
        this.objectMapper = objectMapper;
        this.outboxRepository = outboxRepository;
        this.paymentCacheService = paymentCacheService;
        this.idempotencyService = idempotencyService;
    }

    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest createPaymentRequest,String customerEmail,String idempotencyKey) {

        // idempotency of create payment

        String key = idempotencyService.buildKey(
                customerEmail,
                idempotencyKey
        );

        String requestHash =
                idempotencyService.generateRequestHash(createPaymentRequest);

        IdempotencyDto existingRecord =
                idempotencyService.getRecord(key);

        if (existingRecord != null) {

            if (!existingRecord.requestHash().equals(requestHash)) {
                throw new IdempotencyException(
                        "Idempotency key already used with a different request"
                );
            }

            if ("COMPLETED".equals(existingRecord.status())) {
                try {
                    return objectMapper.readValue(
                            existingRecord.response(),
                            PaymentResponse.class
                    );
                } catch (JsonProcessingException exception) {
                    throw new RuntimeException(
                            "Failed to deserialize idempotent response",
                            exception
                    );
                }
            }

            throw new IdempotencyException(
                    "Payment request is already being processed"
            );
        }

        IdempotencyDto processingRecord =
                new IdempotencyDto(
                        requestHash,
                        "PROCESSING",
                        null,
                        null
                );

        boolean reserved =
                idempotencyService.reserveKey(key, processingRecord);

        if (!reserved) {
            throw new IdempotencyException(
                    "Payment request is already being processed"
            );
        }


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


        PaymentCreatedEvent paymentCreatedEvent=new PaymentCreatedEvent(payment.getPaymentReference(),
                                                payment.getOrderId(),
                                                payment.getCustomerId(),payment.getMerchantId(),
                                                payment.getAmount(),payment.getCurrency(),payment.getCreatedAt(),"PAYMENT-CREATED");

        PaymentResponse paymentResponse=toResponse(paymentRepository.save(payment));


        
       // paymentEventProducer.sendPaymentEvent(paymentCreatedEvent);

        String payload="";
        try {
            payload=objectMapper.writeValueAsString(paymentCreatedEvent);
        }catch (JsonProcessingException exception)
        {
            exception.printStackTrace();
            throw new RuntimeException("Failed to serialize payment event", exception);
        }

        
        OutboxEvent outboxEvent=OutboxEvent.builder().aggregateType("PAYMENT").aggregateId(payment.getPaymentReference())
                                .eventType(paymentCreatedEvent.eventType())
                                .createdAt(LocalDateTime.now()).published(false).paymentEvent(payload).build();


        outboxRepository.save(outboxEvent);

        // redis mark payment status as complete in redis for idempotent key
        idempotencyService.completeKey(
                key,
                requestHash,
                paymentResponse
        );

        return paymentResponse;
    }


    public PaymentResponse getPayment(String paymentReference,String customerEmail){

        PaymentResponse redisData=paymentCacheService.getCachedPayment(paymentReference);
        if(redisData!=null){
            // cache hit
            if(!redisData.customerEmail().equals(customerEmail))
                throw new PaymentNotFoundException("Payment Not Found");
            System.out.println("Data from Redis");
            return redisData;
        }


        // cache miss
        Payment payment=paymentRepository.findByPaymentReferenceAndCustomerEmail(paymentReference,customerEmail)
                .orElseThrow(()->new PaymentNotFoundException("Payment Not Found"));


        PaymentResponse payload=toResponse(payment);

        // populate redis cache
        paymentCacheService.cachePayment(payload);

        return payload;

    }

    public PaymentResponse getPaymentForMerchant(String paymentReference,String merchantId) {

        PaymentResponse redisData=paymentCacheService.getCachedPayment(paymentReference);
        if(redisData!=null){
            // cache hit
            if(!redisData.merchantId().equals(merchantId))
                throw new PaymentNotFoundException("Payment Not Found");
            System.out.println("Data from Redis in merchant get method");
            return redisData;
        }

        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() ->new PaymentNotFoundException("Payment Not Found"));

        if (!payment.getMerchantId().equals(merchantId)) {
            throw new PaymentAccessDeniedException("You do not have access to this payment");
        }

        // populate redis cache

        PaymentResponse payload=toResponse(payment);
        paymentCacheService.cachePayment(payload);
        return payload;
    }

    @Transactional
    public void processPayment(String paymentReference) {

        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException("Payment Not Found"));

        updateStatus(payment, PaymentStatus.PROCESSING);


        PaymentProcessingEvent paymentProcessingEvent=new PaymentProcessingEvent(payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getCustomerId(),payment.getMerchantId(),
                payment.getAmount(),payment.getCurrency(),"processing",payment.getCreatedAt(),"PAYMENT-PROCESSING");

        paymentRepository.save(payment);

        // removing old payment status data from redis
        paymentCacheService.evictPayment(paymentReference);

        String payload="";
        try {
            payload=objectMapper.writeValueAsString(paymentProcessingEvent);
        }catch (JsonProcessingException exception)
        {
            exception.printStackTrace();
            throw new RuntimeException("Failed to serialize payment event", exception);
        }


        OutboxEvent outboxEvent=OutboxEvent.builder().aggregateType("PAYMENT").aggregateId(payment.getPaymentReference())
                .eventType(paymentProcessingEvent.eventType())
                .createdAt(LocalDateTime.now()).published(false).paymentEvent(payload).build();


        outboxRepository.save(outboxEvent);

        // removing old payment status data from redis
        paymentCacheService.evictPayment(paymentReference);


        // paymentEventProducer.sendPaymentEvent(event);
    }

    @Transactional
    public void handlePaymentProcessing(PaymentProcessingEvent event) {

        PaymentProcessingResult result = paymentProcessor.process(event);

        if (result.success()) {

            PaymentSuccessEvent paymentSuccessEvent = new PaymentSuccessEvent(
                    event.paymentReference(),
                    event.orderId(),
                    event.customerId(),
                    event.merchantId(),
                    event.amount(),
                    event.currency(),
                    event.reason(),
                    LocalDateTime.now(),
                    "PAYMENT-SUCCESS"
            );

            String payload="";
            try {
                payload=objectMapper.writeValueAsString(paymentSuccessEvent);
            }catch (JsonProcessingException exception)
            {
                exception.printStackTrace();
                throw new RuntimeException("Failed to serialize payment event", exception);
            }


            OutboxEvent outboxEvent=OutboxEvent.builder().aggregateType("PAYMENT").aggregateId(paymentSuccessEvent.paymentReference())
                    .eventType(paymentSuccessEvent.eventType())
                    .createdAt(LocalDateTime.now()).published(false).paymentEvent(payload).build();


            outboxRepository.save(outboxEvent);



            // paymentEventProducer.sendPaymentEvent(successEvent);

        } else {

            PaymentFailedEvent paymentFailedEvent= new PaymentFailedEvent(
                    event.paymentReference(),
                    event.orderId(),
                    event.customerId(),
                    event.merchantId(),
                    event.amount(),
                    event.currency(),
                    result.reason(),
                    LocalDateTime.now(),
                    "PAYMENT-FAILED"
            );

            String payload="";
            try {
                payload=objectMapper.writeValueAsString(paymentFailedEvent);
            }catch (JsonProcessingException exception)
            {
                exception.printStackTrace();
                throw new RuntimeException("Failed to serialize payment event", exception);
            }


            OutboxEvent outboxEvent=OutboxEvent.builder().aggregateType("PAYMENT").aggregateId(paymentFailedEvent.paymentReference())
                    .eventType(paymentFailedEvent.eventType())
                    .createdAt(LocalDateTime.now()).published(false).paymentEvent(payload).build();


            outboxRepository.save(outboxEvent);

            // paymentEventProducer.sendPaymentEvent(failedEvent);
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


    @Transactional
    public void markPaymentSuccess(String paymentReference) {

        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() ->new PaymentNotFoundException("Payment Not Found"));


        updateStatus(payment, PaymentStatus.SUCCESS);

        paymentRepository.save(payment);

        // removing old payment status data from redis
        paymentCacheService.evictPayment(paymentReference);

    }


    @Transactional
    public void markPaymentFailed(String paymentReference) {

        Payment payment = paymentRepository
                .findByPaymentReference(paymentReference)
                .orElseThrow(() ->
                        new PaymentNotFoundException("Payment Not Found")
                );

        updateStatus(payment, PaymentStatus.FAILED);

        paymentRepository.save(payment);

        // removing old payment status data from redis
        paymentCacheService.evictPayment(paymentReference);
    }

    @Transactional
    public void refundPayment(String paymentReference,String merchantId) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new PaymentNotFoundException("Payment Not Found"));

        // Merchant ownership check
        if (!payment.getMerchantId().equals(merchantId)) {
            throw new PaymentAccessDeniedException("You do not have access to this payment");
        }

        // SUCCESS → REFUNDED
        updateStatus(payment, PaymentStatus.REFUNDED);


        PaymentRefundEvent paymentRefundEvent = new PaymentRefundEvent(
                payment.getPaymentReference(),
                payment.getOrderId(),
                payment.getCustomerId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getUpdatedAt(),
                "PAYMENT-REFUNDED"
        );

        paymentRepository.save(payment);



        String payload="";
        try {
            payload=objectMapper.writeValueAsString(paymentRefundEvent);
        }catch (JsonProcessingException exception)
        {
            exception.printStackTrace();
            throw new RuntimeException("Failed to serialize payment event", exception);
        }


        OutboxEvent outboxEvent=OutboxEvent.builder().aggregateType("PAYMENT").aggregateId(payment.getPaymentReference())
                .eventType(paymentRefundEvent.eventType())
                .createdAt(LocalDateTime.now()).published(false).paymentEvent(payload).build();


        outboxRepository.save(outboxEvent);

        // removing old payment status data from redis
        paymentCacheService.evictPayment(paymentReference);

       //  paymentEventProducer.sendPaymentEvent(event);
    }
}

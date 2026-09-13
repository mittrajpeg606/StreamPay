package com.streampay.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.kafka.dto.*;
import com.streampay.payment.service.PaymentProcessor;
import com.streampay.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final PaymentProcessor paymentProcessor;
    private final ObjectMapper objectMapper;

    public PaymentEventConsumer(PaymentService paymentService,PaymentProcessor paymentProcessor,ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.paymentProcessor = paymentProcessor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "payment-events",groupId = "payment-service-group")
    public void consumePaymentEvent(String kafkaObject) {

        System.out.println("Consumed event: " + kafkaObject);

        try {
            JsonNode paymentEvent = objectMapper.readTree(kafkaObject);

            String eventType = paymentEvent.get("eventType").asText();

            switch (eventType) {

                case "PAYMENT-CREATED" -> {
                    PaymentCreatedEvent event = objectMapper.treeToValue(paymentEvent, PaymentCreatedEvent.class);

                    paymentService.processPayment(event.paymentReference());
                }

                case "PAYMENT-PROCESSING" -> {
                    PaymentProcessingEvent event = objectMapper.treeToValue(paymentEvent, PaymentProcessingEvent.class);

                    paymentService.handlePaymentProcessing(event);
                }

                case "PAYMENT-SUCCESS" -> {
                    PaymentSuccessEvent event = objectMapper.treeToValue(paymentEvent, PaymentSuccessEvent.class);

                    paymentService.markPaymentSuccess(event.paymentReference());
                }

                case "PAYMENT-FAILED" -> {PaymentFailedEvent event =objectMapper.treeToValue(paymentEvent,PaymentFailedEvent.class);

                    paymentService.markPaymentFailed(event.paymentReference());
                }

                case "PAYMENT-REFUNDED" -> {
                    PaymentRefundEvent event = objectMapper.treeToValue(paymentEvent,PaymentRefundEvent.class);

                    System.out.println("Payment refunded: "+ event.paymentReference());
                }

                default -> System.out.println("Unknown payment event type: " + eventType);
            }

        } catch (Exception exception) {
            System.err.println("Failed to consume payment event");
            exception.printStackTrace();
        }
    }
}
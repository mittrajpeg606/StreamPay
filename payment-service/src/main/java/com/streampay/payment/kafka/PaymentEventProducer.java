package com.streampay.payment.kafka;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.kafka.dto.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventProducer  {

    private static final String TOPIC="payment-events";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PaymentEventProducer(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public PaymentEvent sendPaymentEvent(PaymentEvent paymentEvent) {

        String event="";
        try {
            event=objectMapper.writeValueAsString(paymentEvent);
        }catch (JsonProcessingException exception)
        {
            exception.printStackTrace();
            throw new RuntimeException("Failed to serialize payment event", exception);
        }

        kafkaTemplate.send(TOPIC,paymentEvent.paymentReference(),event);
        return paymentEvent;
    }
}

package com.streampay.payment.kafka;


import com.streampay.payment.kafka.dto.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventProducer  {

    private static final String TOPIC="payment-events";
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public PaymentEvent sendPaymentEvent(PaymentEvent paymentEvent)
    {
        kafkaTemplate.send(TOPIC,paymentEvent.paymentReference(),paymentEvent);
        return paymentEvent;
    }
}

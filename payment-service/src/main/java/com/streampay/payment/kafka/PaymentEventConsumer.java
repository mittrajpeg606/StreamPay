package com.streampay.payment.kafka;

import com.streampay.payment.kafka.dto.*;
import com.streampay.payment.service.PaymentProcessingResult;
import com.streampay.payment.service.PaymentProcessor;
import com.streampay.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final PaymentProcessor paymentProcessor;

    public PaymentEventConsumer(PaymentService paymentService, PaymentProcessor paymentProcessor) {
        this.paymentService = paymentService;
        this.paymentProcessor = paymentProcessor;
    }

    @KafkaListener(topics = "payment-events",groupId = "payment-service-group")
    public void consumePaymentEvent(PaymentEvent event)
    {
        System.out.println("Consumed event" + event);
        if (event instanceof PaymentCreatedEvent createdEvent) {
            paymentService.processPayment(createdEvent.paymentReference());
        }else if (event instanceof PaymentProcessingEvent processingEvent) {

            paymentService.handlePaymentProcessing(processingEvent);
        }
        else if (event instanceof PaymentSuccessEvent successEvent) {

            paymentService.markPaymentSuccess(successEvent.paymentReference()
            );

        } else if (event instanceof PaymentFailedEvent failedEvent) {

            paymentService.markPaymentFailed(failedEvent.paymentReference()
            );
        }
        else if (event instanceof PaymentRefundEvent refundEvent) {
            System.out.println("Payment refunded: " + refundEvent.paymentReference()
            );
        }
    }
}

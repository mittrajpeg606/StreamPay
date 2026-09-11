package com.streampay.payment.service;

import com.streampay.payment.kafka.dto.PaymentProcessingEvent;
import org.springframework.stereotype.Service;

@Service
public class PaymentProcessorImpl implements PaymentProcessor {

    @Override
    public PaymentProcessingResult process(PaymentProcessingEvent event) {

        // Temporary payment gateway simulation
        boolean success = true;
        // success is implemented by default

        if (success) {
            return new PaymentProcessingResult(event.paymentReference(),true,null);
        }

        return new PaymentProcessingResult(event.paymentReference(),false,"Payment gateway declined");
    }
}
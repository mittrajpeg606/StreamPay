package com.streampay.payment.reconciliation;


import org.springframework.stereotype.Service;

@Service
public class ExternalPaymentProviderImpl implements ExternalPaymentProvider {

    @Override
    public ExternalPaymentStatus getPaymentStatus(String paymentReference) {
        return ExternalPaymentStatus.SUCCESS;
    }
}
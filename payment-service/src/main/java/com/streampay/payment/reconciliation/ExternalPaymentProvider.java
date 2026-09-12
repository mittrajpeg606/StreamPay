package com.streampay.payment.reconciliation;

public interface ExternalPaymentProvider {

    ExternalPaymentStatus getPaymentStatus(
            String paymentReference
    );
}
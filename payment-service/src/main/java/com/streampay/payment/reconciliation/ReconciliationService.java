package com.streampay.payment.reconciliation;

import com.streampay.payment.entities.Payment;
import com.streampay.payment.enums.PaymentStatus;
import com.streampay.payment.exception.PaymentNotFoundException;
import com.streampay.payment.exception.ReconciliationException;
import com.streampay.payment.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ReconciliationService {

    private final PaymentRepository paymentRepository;
    private final ExternalPaymentProvider externalPaymentProvider;

    public ReconciliationService(PaymentRepository paymentRepository, ExternalPaymentProvider externalPaymentProvider) {
        this.paymentRepository = paymentRepository;
        this.externalPaymentProvider = externalPaymentProvider;
    }


    public void reconcile(String paymentReferrence){
        // get status from DB
        Payment payment=paymentRepository.findByPaymentReference(paymentReferrence).orElseThrow(()->new PaymentNotFoundException("Payment Not Found"));
        // get from external payment provider
        String externalStatus=externalPaymentProvider.getPaymentStatus(paymentReferrence).toString();
        String statusFromDB=payment.getStatus().toString();

        if(statusFromDB.equals(externalStatus)){
          return;
        } else if(isValidReconciliationTransition(PaymentStatus.valueOf(statusFromDB),ExternalPaymentStatus.valueOf(externalStatus))){
            payment.setStatus(PaymentStatus.valueOf(externalStatus));
            payment.setUpdatedAt(LocalDateTime.now());
            paymentRepository.save(payment);
        }else{
            throw new ReconciliationException("invalid reconciliation state"+ statusFromDB + " -> "+ externalStatus);
        }

    }

    private boolean isValidReconciliationTransition(PaymentStatus currentStatus,ExternalPaymentStatus externalStatus) {
        return switch (currentStatus) {

            case CREATED ->
                    externalStatus == ExternalPaymentStatus.PROCESSING;

            case PROCESSING ->
                    externalStatus == ExternalPaymentStatus.SUCCESS
                            || externalStatus == ExternalPaymentStatus.FAILED;

            case SUCCESS ->
                    externalStatus == ExternalPaymentStatus.REFUNDED;

            case FAILED, REFUNDED -> false;
        };
    }


}

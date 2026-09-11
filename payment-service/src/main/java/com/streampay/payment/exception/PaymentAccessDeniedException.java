package com.streampay.payment.exception;



public class PaymentAccessDeniedException extends RuntimeException{

    public PaymentAccessDeniedException(String message)
    {
        super(message);
    }
}

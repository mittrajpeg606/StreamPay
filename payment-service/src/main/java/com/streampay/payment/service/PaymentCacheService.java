package com.streampay.payment.service;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.dto.PaymentResponse;
import com.streampay.payment.exception.PaymentNotFoundException;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Duration;

@Service
@AllArgsConstructor
public class PaymentCacheService {

    private final static String CACHE_KEY="payment:";


    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final RedisTemplate<String,String> redisTemplate;
    private final ObjectMapper objectMapper;


    // cachePayment() and getCachedPayment()


    public void cachePayment(PaymentResponse paymentResponse)
    {
        String key = CACHE_KEY + paymentResponse.paymentReference();
        try {
            String value = objectMapper.writeValueAsString(paymentResponse);
            redisTemplate.opsForValue().set(key,value,CACHE_TTL);
        }catch (JsonProcessingException exception){
            throw new RuntimeException("Failed to serialize payment for cache");
        }

    }


    public PaymentResponse getCachedPayment(String paymentReference){
        String key=CACHE_KEY + paymentReference;
        PaymentResponse payment;
        try {
            String value=redisTemplate.opsForValue().get(key);
            if(value==null)
            {
              return null;
            }
            payment=objectMapper.readValue(value,PaymentResponse.class);
        }catch (JsonProcessingException exception){
            throw new RuntimeException("Failed to serialize payment for cache");
        }

        return payment;
    }

    public void evictPayment(String paymentReference) {

        String key = CACHE_KEY + paymentReference;

        redisTemplate.delete(key);
    }
}

package com.streampay.payment.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streampay.payment.dto.PaymentResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public String buildKey(String customerId, String idempotencyKey) {
        return KEY_PREFIX + customerId + ":" + idempotencyKey;
    }

    public String generateRequestHash(Object request) {
        try {
            String json = objectMapper.writeValueAsString(request);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    json.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder hexString = new StringBuilder();

            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    hexString.append('0');
                }

                hexString.append(hex);
            }

            return hexString.toString();

        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new RuntimeException(
                    "Failed to generate idempotency request hash",
                    exception
            );
        }
    }

    public boolean reserveKey(String key, IdempotencyDto record) {
        try {
            String value = objectMapper.writeValueAsString(record);

            Boolean reserved = redisTemplate
                    .opsForValue()
                    .setIfAbsent(
                            key,
                            value,
                            PROCESSING_TTL
                    );

            return Boolean.TRUE.equals(reserved);

        } catch (JsonProcessingException exception) {
            throw new RuntimeException(
                    "Failed to store idempotency record",
                    exception
            );
        }
    }

    public IdempotencyDto getRecord(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return null;
            }

            return objectMapper.readValue(
                    value,
                    IdempotencyDto.class
            );

        } catch (JsonProcessingException exception) {
            throw new RuntimeException(
                    "Failed to read idempotency record",
                    exception
            );
        }
    }

    public void completeKey(
            String key,
            String requestHash,
            PaymentResponse paymentResponse
    ) {
        try {
            String responseJson =
                    objectMapper.writeValueAsString(paymentResponse);

            IdempotencyDto record = new IdempotencyDto(
                    requestHash,
                    "COMPLETED",
                    paymentResponse.paymentReference(),
                    responseJson
            );

            String value = objectMapper.writeValueAsString(record);

            redisTemplate.opsForValue().set(
                    key,
                    value,
                    Duration.ofHours(24)
            );

        } catch (JsonProcessingException exception) {
            throw new RuntimeException(
                    "Failed to store completed idempotency record",
                    exception
            );
        }
    }
}
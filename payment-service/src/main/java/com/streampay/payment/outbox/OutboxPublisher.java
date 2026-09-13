package com.streampay.payment.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxPublisher {

    private static final String TOPIC="payment-events";

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }


    @Scheduled(fixedDelay = 5000)
    private void publishKafkaEvents()
    {

        List<OutboxEvent> events=outboxRepository.findTop100ByPublishedFalseOrderByCreatedAtAsc();

           events.forEach(event->{

               try{
                   kafkaTemplate.send(TOPIC,event.getAggregateId(),event.getPaymentEvent()).whenComplete((result,exception)->{
                       if(exception==null){
                           event.setPublished(true);
                           outboxRepository.save(event);
                       }
                   });
               }catch(Exception exception){
                   throw new RuntimeException("Failed to publish event in Kafka "+ event.getAggregateId());
               }

           });

    }
}

package at.fhtw.service;

import at.fhtw.config.RabbitConfig;
import at.fhtw.exception.QueueException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
public class DocumentQueueSender {

    private final RabbitTemplate rabbitTemplate;

    public DocumentQueueSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendDocumentUploaded(Long documentId) {
        String message = "{\"documentId\":" + documentId + ",\"uploadedAt\":\"" + LocalDateTime.now() + "\"}";
        log.info("Sending queue message to exchange={}, routingKey={}, message={}",
                RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        } catch (Exception ex) {
            log.error("Failed to send message to RabbitMQ for document id={}", documentId, ex);
            throw new QueueException("Failed to send message to RabbitMQ for document id: " + documentId, ex);
        }
    }
}
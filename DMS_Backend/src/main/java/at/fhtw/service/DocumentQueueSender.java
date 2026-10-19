package at.fhtw.service;

import at.fhtw.config.RabbitConfig;
import at.fhtw.exception.QueueException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DocumentQueueSender {

    private final RabbitTemplate rabbitTemplate;

    public DocumentQueueSender(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendDocumentUploaded(Long documentId) {
        String message = "{\"documentId\":" + documentId + ",\"uploadedAt\":\"" + LocalDateTime.now() + "\"}";
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        } catch (AmqpException ex) {
            throw new QueueException("Failed to send message to RabbitMQ for document id: " + documentId, ex);
        }
    }
}

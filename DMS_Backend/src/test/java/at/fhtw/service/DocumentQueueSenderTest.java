package at.fhtw.service;

import at.fhtw.config.RabbitConfig;
import at.fhtw.exception.QueueException;
import org.springframework.amqp.AmqpException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class DocumentQueueSenderTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private DocumentQueueSender queueSender;

    @Test
    void sendDocumentUploaded_shouldSendMessageToExchange() {
        queueSender.sendDocumentUploaded(42L);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.EXCHANGE),
                eq(RabbitConfig.ROUTING_KEY),
                anyString()
        );
    }

    @Test
    void sendDocumentUploaded_shouldWrapBrokerFailure() {
        doThrow(new AmqpException("offline")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), anyString());
        assertThrows(QueueException.class, () -> queueSender.sendDocumentUploaded(42L));
    }
}

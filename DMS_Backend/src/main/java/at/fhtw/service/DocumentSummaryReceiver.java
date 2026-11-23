package at.fhtw.service;

import at.fhtw.config.RabbitConfig;
import at.fhtw.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;

@Component
@Slf4j
public class DocumentSummaryReceiver {
    private final DocumentRepository documents;
    private final ObjectMapper json;

    public DocumentSummaryReceiver(DocumentRepository documents, ObjectMapper json) {
        this.documents = documents;
        this.json = json;
    }

    @RabbitListener(queuesToDeclare = @Queue(RabbitConfig.SUMMARY_QUEUE))
    @Transactional
    public void receive(Message message) {
        SummaryResult result;
        try {
            result = json.readValue(message.getBody(), SummaryResult.class);
        } catch (JacksonException ex) {
            log.warn("Ignoring malformed summary result");
            return;
        }
        if (result == null || result.documentId() == null || result.documentId() <= 0
                || result.objectKey() == null || result.objectKey().isBlank()) {
            log.warn("Ignoring invalid summary result");
            return;
        }
        if (result.summarizing() != null && result.summary() == null) {
            documents.setSummaryStartedAt(result.documentId(), result.objectKey(),
                    result.summarizing() ? Instant.now() : null);
            return;
        }
        if (result.summarizing() != null || result.summary() == null || result.summary().isBlank()
                || result.summary().length() > 5000 || result.summary().indexOf('\0') >= 0) {
            log.warn("Ignoring invalid summary result");
            return;
        }

        if (documents.storeSummary(result.documentId(), result.objectKey(), result.summary(), LocalDateTime.now()) == 1) {
            log.info("Stored summary for document id={}", result.documentId());
        } else {
            log.info("Ignoring duplicate or stale summary for document id={}", result.documentId());
        }
    }

    private record SummaryResult(Long documentId, String objectKey, String summary, Boolean summarizing) {}
}

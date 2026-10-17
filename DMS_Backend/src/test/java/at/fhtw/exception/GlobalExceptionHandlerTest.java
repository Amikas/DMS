package at.fhtw.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleQueueException_shouldReturn500() {
        QueueException ex = new QueueException("RabbitMQ unavailable");
        ResponseEntity<Map<String, Object>> result = handler.handleQueueException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        Map<String, Object> body = result.getBody();
        assertNotNull(body);
        assertEquals("Queue Error", body.get("error"));
        assertEquals("RabbitMQ unavailable", body.get("message"));
    }

    @Test
    void handleDocumentServiceException_shouldReturn500() {
        DocumentServiceException ex = new DocumentServiceException("Service failed");
        ResponseEntity<Map<String, Object>> result = handler.handleDocumentServiceException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        Map<String, Object> body = result.getBody();
        assertNotNull(body);
        assertEquals("Document Service Error", body.get("error"));
        assertEquals("Service failed", body.get("message"));
    }

    @Test
    void handleRepositoryException_shouldReturn500() {
        RepositoryException ex = new RepositoryException("DB error");
        ResponseEntity<Map<String, Object>> result = handler.handleRepositoryException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, result.getStatusCode());
        Map<String, Object> body = result.getBody();
        assertNotNull(body);
        assertEquals("Repository Error", body.get("error"));
        assertEquals("DB error", body.get("message"));
    }
}

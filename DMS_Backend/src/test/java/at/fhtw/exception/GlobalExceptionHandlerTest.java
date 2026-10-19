package at.fhtw.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test
    void infrastructureFailuresAreLoggedWithoutExposingDetails() {
        for (RuntimeException ex : List.of(new QueueException("secret"),
                new DocumentServiceException("secret"), new DataAccessResourceFailureException("secret"))) {
            var response = new GlobalExceptionHandler().handleInfrastructureException(ex);
            assertEquals(500, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertFalse(response.getBody().toString().contains("secret"));
        }
    }
}

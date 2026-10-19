package at.fhtw.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(field ->
                errors.put(field.getField(), field.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("timestamp", LocalDateTime.now(),
                "status", 400, "error", "Bad Request", "validationErrors", errors));
    }

    @ExceptionHandler({QueueException.class, DocumentServiceException.class, DataAccessException.class})
    public ResponseEntity<Map<String, Object>> handleInfrastructureException(RuntimeException ex) {
        log.error("Document operation failed", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Document operation failed; please retry.");
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("timestamp", LocalDateTime.now(),
                "status", status.value(), "error", status.getReasonPhrase(), "message", message));
    }
}

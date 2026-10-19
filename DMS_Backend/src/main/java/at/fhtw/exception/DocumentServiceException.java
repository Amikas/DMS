package at.fhtw.exception;

public class DocumentServiceException extends RuntimeException {
    public DocumentServiceException(String message) {
        super(message);
    }

    public DocumentServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

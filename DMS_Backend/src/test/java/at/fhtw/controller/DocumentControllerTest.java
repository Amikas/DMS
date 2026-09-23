package at.fhtw.controller;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @InjectMocks
    private DocumentController controller;

    @Test
    void getAll_shouldReturnList() {
        DocumentResponse response = DocumentResponse.builder().id(1L).title("Doc1").build();
        when(documentService.getAll()).thenReturn(List.of(response));

        ResponseEntity<List<DocumentResponse>> result = controller.getAll();

        assertEquals(200, result.getStatusCode().value());
        assertEquals(1, result.getBody().size());
        assertEquals("Doc1", result.getBody().get(0).getTitle());
    }

    @Test
    void getById_shouldReturnDocument() {
        DocumentResponse response = DocumentResponse.builder().id(1L).title("Doc1").build();
        when(documentService.getById(1L)).thenReturn(response);

        ResponseEntity<DocumentResponse> result = controller.getById(1L);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("Doc1", result.getBody().getTitle());
    }

    @Test
    void create_shouldReturnCreated() {
        DocumentRequest request = new DocumentRequest();
        request.setTitle("NewDoc");
        DocumentResponse response = DocumentResponse.builder().id(1L).title("NewDoc").build();
        when(documentService.create(any(DocumentRequest.class))).thenReturn(response);

        ResponseEntity<DocumentResponse> result = controller.create(request);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("NewDoc", result.getBody().getTitle());
    }

    @Test
    void update_shouldReturnUpdated() {
        DocumentRequest request = new DocumentRequest();
        request.setTitle("Updated");
        when(documentService.update(eq(1L), any(DocumentRequest.class))).thenReturn(DocumentResponse.builder().id(1L).title("Updated").build());

        ResponseEntity<DocumentResponse> result = controller.update(1L, request);

        assertEquals(200, result.getStatusCode().value());
        assertEquals("Updated", result.getBody().getTitle());
    }

    @Test
    void delete_shouldReturnNoContent() {
        doNothing().when(documentService).delete(1L);

        ResponseEntity<Void> result = controller.delete(1L);

        assertEquals(204, result.getStatusCode().value());
    }
}

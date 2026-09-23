package at.fhtw.service;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.entity.Collection;
import at.fhtw.entity.CollectionDocument;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private CollectionDocumentRepository memberships;

    @Mock
    private DocumentMapper documentMapper;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void getById_shouldReturnDocument_whenExists() {
        Document document = new Document();
        document.setId(1L);
        document.setTitle("Test");
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(documentMapper.toResponse(any(Document.class))).thenReturn(DocumentResponse.builder().id(1L).title("Test").build());

        DocumentResponse response = documentService.getById(1L);

        assertNotNull(response);
        assertEquals("Test", response.getTitle());
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.getById(999L));
    }

    @Test
    void create_shouldSetStatus() {
        DocumentRequest request = new DocumentRequest();
        request.setTitle("NewDoc");
        Document document = new Document();
        document.setId(1L);
        document.setTitle("NewDoc");
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document d = invocation.getArgument(0);
            d.setId(1L);
            return d;
        });
        when(documentMapper.toEntity(any(DocumentRequest.class))).thenReturn(document);
        when(documentMapper.toResponse(any(Document.class))).thenReturn(DocumentResponse.builder().id(1L).title("NewDoc").build());

        DocumentResponse response = documentService.create(request);

        assertNotNull(response);
        assertEquals("NewDoc", response.getTitle());
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(documentRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> documentService.delete(999L));
    }

    @Test
    void delete_shouldRemoveCollectionMembershipsFirst() {
        Document document = new Document();
        document.setId(1L);
        CollectionDocument link = new CollectionDocument(new Collection("Work"), document);
        when(documentRepository.existsById(1L)).thenReturn(true);
        when(memberships.findByDocument_Id(1L)).thenReturn(List.of(link));

        documentService.delete(1L);

        var order = inOrder(memberships, documentRepository);
        order.verify(memberships).deleteAll(List.of(link));
        order.verify(documentRepository).deleteById(1L);
    }
}

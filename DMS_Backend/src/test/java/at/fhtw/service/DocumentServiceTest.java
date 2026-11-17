package at.fhtw.service;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.entity.Collection;
import at.fhtw.entity.CollectionDocument;
import at.fhtw.exception.DocumentServiceException;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.exception.QueueException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
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

    @Mock
    private DocumentQueueSender queueSender;

    @Mock
    private ObjectStorageService objectStorage;

    @Mock
    private SearchService searchService;

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
    void create_shouldFail_whenQueueSendFails() {
        DocumentRequest request = DocumentRequest.builder().title("NewDoc").build();
        Document document = Document.builder().id(1L).title("NewDoc").build();
        when(documentMapper.toEntity(request)).thenReturn(document);
        when(documentRepository.save(document)).thenReturn(document);
        doThrow(new QueueException("Rabbit down")).when(queueSender).sendDocumentUploaded(eq(1L), any());

        assertThrows(QueueException.class, () -> documentService.create(request));
    }

    @Test
    void upload_shouldStorePdfInObjectStorageAndEnqueueJob() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            DocumentRequest request = DocumentRequest.builder().title("HelloWorld").build();
            MockMultipartFile file = new MockMultipartFile("file", "hello.pdf", "application/pdf",
                    "%PDF-1.4 hello".getBytes(StandardCharsets.US_ASCII));
            when(documentMapper.toEntity(any(DocumentRequest.class))).thenAnswer(invocation -> {
                DocumentRequest req = invocation.getArgument(0);
                Document document = new Document();
                document.setTitle(req.getTitle());
                document.setFilePath(req.getFilePath());
                return document;
            });
            when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
                Document saved = invocation.getArgument(0);
                saved.setId(1L);
                return saved;
            });
            when(documentMapper.toResponse(any(Document.class))).thenReturn(DocumentResponse.builder().id(1L).title("HelloWorld").build());

            documentService.upload(request, file);

            verify(objectStorage).store(argThat(key -> key.endsWith(".pdf")), any(byte[].class));
            verify(queueSender).sendDocumentUploaded(eq(1L), argThat(key -> key != null && key.endsWith(".pdf")));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> documentService.delete(999L));

        verifyNoInteractions(searchService, objectStorage, memberships);
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_shouldRemoveSearchEntryPdfAndDatabaseRows() {
        Document document = Document.builder().id(1L).filePath("stored.pdf").build();
        CollectionDocument link = new CollectionDocument(new Collection("Work"), document);
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(memberships.findByDocument_Id(1L)).thenReturn(List.of(link));

        documentService.delete(1L);

        var order = inOrder(searchService, objectStorage, memberships, documentRepository);
        order.verify(searchService).delete(1L);
        order.verify(objectStorage).remove("stored.pdf");
        order.verify(memberships).deleteAll(List.of(link));
        order.verify(documentRepository).deleteById(1L);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void delete_shouldSkipStorage_whenFilePathIsMissing(String filePath) {
        Document document = Document.builder().id(1L).filePath(filePath).build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        when(memberships.findByDocument_Id(1L)).thenReturn(List.of());

        documentService.delete(1L);

        verifyNoInteractions(objectStorage);
        verify(searchService).delete(1L);
        verify(memberships).deleteAll(List.of());
        verify(documentRepository).deleteById(1L);
    }

    @Test
    void delete_shouldKeepStorageAndDatabase_whenSearchCleanupFails() {
        Document document = Document.builder().id(1L).filePath("stored.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        doThrow(new DocumentServiceException("search down")).when(searchService).delete(1L);

        assertThrows(DocumentServiceException.class, () -> documentService.delete(1L));

        verifyNoInteractions(objectStorage, memberships);
        verify(documentRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_shouldKeepDatabase_whenStorageCleanupFails() {
        Document document = Document.builder().id(1L).filePath("stored.pdf").build();
        when(documentRepository.findById(1L)).thenReturn(Optional.of(document));
        doThrow(new DocumentServiceException("storage down")).when(objectStorage).remove("stored.pdf");

        assertThrows(DocumentServiceException.class, () -> documentService.delete(1L));

        verify(searchService).delete(1L);
        verifyNoInteractions(memberships);
        verify(documentRepository, never()).deleteById(anyLong());
    }
}

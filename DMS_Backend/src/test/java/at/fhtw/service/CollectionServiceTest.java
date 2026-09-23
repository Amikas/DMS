package at.fhtw.service;

import at.fhtw.dto.CollectionRequest;
import at.fhtw.dto.CollectionResponse;
import at.fhtw.entity.Collection;
import at.fhtw.entity.CollectionDocument;
import at.fhtw.entity.Document;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.CollectionRepository;
import at.fhtw.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionServiceTest {
    @Mock private CollectionRepository collections;
    @Mock private CollectionDocumentRepository memberships;
    @Mock private DocumentRepository documents;
    @InjectMocks private CollectionService service;

    @Test
    void addDocumentCreatesMembershipOnlyOnce() {
        Collection collection = collection(1L, "Work");
        Document document = new Document();
        document.setId(2L);
        when(collections.findById(1L)).thenReturn(Optional.of(collection));
        when(documents.findById(2L)).thenReturn(Optional.of(document));
        when(memberships.findByCollection_Id(1L))
                .thenReturn(List.of(new CollectionDocument(collection, document)));

        CollectionResponse result = service.addDocument(1L, 2L);

        assertEquals(List.of(2L), result.documentIds());
        verify(memberships).save(any(CollectionDocument.class));
    }

    @Test
    void addingExistingDocumentDoesNotCreateDuplicate() {
        Collection collection = collection(1L, "Work");
        Document document = new Document();
        document.setId(2L);
        when(collections.findById(1L)).thenReturn(Optional.of(collection));
        when(documents.findById(2L)).thenReturn(Optional.of(document));
        when(memberships.existsByCollection_IdAndDocument_Id(1L, 2L)).thenReturn(true);
        when(memberships.findByCollection_Id(1L))
                .thenReturn(List.of(new CollectionDocument(collection, document)));

        assertEquals(List.of(2L), service.addDocument(1L, 2L).documentIds());
        verify(memberships, never()).save(any());
    }

    @Test
    void deletingCollectionOnlyDeletesMembershipsAndCollection() {
        Collection collection = collection(1L, "Work");
        Document document = new Document();
        document.setId(2L);
        List<CollectionDocument> links = List.of(new CollectionDocument(collection, document));
        when(collections.findById(1L)).thenReturn(Optional.of(collection));
        when(memberships.findByCollection_Id(1L)).thenReturn(links);

        service.delete(1L);

        verify(memberships).deleteAll(links);
        verify(collections).delete(collection);
        verifyNoInteractions(documents);
    }

    @Test
    void missingDocumentCannotBeAdded() {
        when(collections.findById(1L)).thenReturn(Optional.of(collection(1L, "Work")));
        when(documents.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.addDocument(1L, 99L));
        verify(memberships, never()).save(any());
    }

    @Test
    void renameUpdatesExistingCollection() {
        Collection collection = collection(1L, "Old");
        when(collections.findById(1L)).thenReturn(Optional.of(collection));
        when(collections.save(collection)).thenReturn(collection);

        assertEquals("New", service.rename(1L, new CollectionRequest("New")).name());
    }

    private static Collection collection(Long id, String name) {
        Collection collection = new Collection(name);
        collection.setId(id);
        return collection;
    }
}

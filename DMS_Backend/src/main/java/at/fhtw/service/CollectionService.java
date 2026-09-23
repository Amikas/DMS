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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CollectionService {
    private final CollectionRepository collections;
    private final CollectionDocumentRepository memberships;
    private final DocumentRepository documents;

    public CollectionService(CollectionRepository collections, CollectionDocumentRepository memberships,
                             DocumentRepository documents) {
        this.collections = collections;
        this.memberships = memberships;
        this.documents = documents;
    }

    public List<CollectionResponse> getAll() {
        return collections.findAll().stream().map(this::toResponse).toList();
    }

    public CollectionResponse getById(Long id) {
        return toResponse(findCollection(id));
    }

    public CollectionResponse create(CollectionRequest request) {
        return toResponse(collections.save(new Collection(request.name())));
    }

    public CollectionResponse rename(Long id, CollectionRequest request) {
        Collection collection = findCollection(id);
        collection.setName(request.name());
        return toResponse(collections.save(collection));
    }

    public void delete(Long id) {
        Collection collection = findCollection(id);
        memberships.deleteAll(memberships.findByCollection_Id(id));
        collections.delete(collection);
    }

    public CollectionResponse addDocument(Long id, Long documentId) {
        Collection collection = findCollection(id);
        Document document = documents.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + documentId));
        if (!memberships.existsByCollection_IdAndDocument_Id(id, documentId)) {
            memberships.save(new CollectionDocument(collection, document));
        }
        return toResponse(collection);
    }

    public void removeDocument(Long id, Long documentId) {
        findCollection(id);
        memberships.findByCollection_IdAndDocument_Id(id, documentId).ifPresent(memberships::delete);
    }

    private Collection findCollection(Long id) {
        return collections.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Collection not found with id: " + id));
    }

    private CollectionResponse toResponse(Collection collection) {
        List<Long> documentIds = memberships.findByCollection_Id(collection.getId()).stream()
                .map(membership -> membership.getDocument().getId()).toList();
        return new CollectionResponse(collection.getId(), collection.getName(), documentIds);
    }
}

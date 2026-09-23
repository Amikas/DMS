package at.fhtw.service;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.dto.TagResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Comparator;
import java.util.stream.Collectors;

@Service
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CollectionDocumentRepository memberships;
    private final DocumentMapper mapper;

    public DocumentService(DocumentRepository documentRepository, CollectionDocumentRepository memberships,
                           DocumentMapper mapper) {
        this.documentRepository = documentRepository;
        this.memberships = memberships;
        this.mapper = mapper;
    }

    public List<DocumentResponse> getAll() {
        return documentRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public DocumentResponse getById(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        return toResponse(document);
    }

    public DocumentResponse create(DocumentRequest request) {
        Document document = mapper.toEntity(request);
        document.setStatus(DocumentStatus.UPLOADED);
        Document saved = documentRepository.save(document);
        return toResponse(saved);
    }

    public DocumentResponse update(Long id, DocumentRequest request) {
        Document existing = documentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setFilePath(request.getFilePath());
        existing.setFileName(request.getFileName());
        existing.setFileSize(request.getFileSize());
        existing.setContentType(request.getContentType());
        Document updated = documentRepository.save(existing);
        return toResponse(updated);
    }

    public void delete(Long id) {
        if (!documentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Document not found with id: " + id);
        }
        memberships.deleteAll(memberships.findByDocument_Id(id));
        documentRepository.deleteById(id);
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse response = mapper.toResponse(document);
        response.setTags(document.getTags().stream()
                .map(tag -> new TagResponse(tag.getId(), tag.getName()))
                .sorted(Comparator.comparing(TagResponse::name))
                .toList());
        return response;
    }
}

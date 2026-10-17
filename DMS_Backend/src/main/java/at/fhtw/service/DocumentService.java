package at.fhtw.service;

import at.fhtw.exception.QueueException;
import at.fhtw.service.DocumentQueueSender;
import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final CollectionDocumentRepository memberships;
    private final DocumentQueueSender queueSender;
    private final DocumentMapper mapper;

    public DocumentService(DocumentRepository documentRepository, CollectionDocumentRepository memberships,
                           DocumentMapper mapper, DocumentQueueSender queueSender) {
        this.documentRepository = documentRepository;
        this.memberships = memberships;
        this.mapper = mapper;
        this.queueSender = queueSender;

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
        log.info("Creating document: title='{}'", request.getTitle());
        Document document = mapper.toEntity(request);
        document.setStatus(DocumentStatus.UPLOADED);
        Document saved = documentRepository.save(document);
        log.info("Document saved with id={}", saved.getId());
        try {
            queueSender.sendDocumentUploaded(saved.getId());
            log.info("Queue message sent for document id={}", saved.getId());
        } catch (Exception ex) {
            log.error("Failed to send queue message for document id={}", saved.getId(), ex);
        }
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
        return mapper.toResponse(document);
    }
}

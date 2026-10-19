package at.fhtw.service;

import at.fhtw.exception.DocumentServiceException;
import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.CollectionDocumentRepository;
import at.fhtw.repository.DocumentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Value("${dms.storage-path:uploads}")
    private Path storagePath;

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
        Document document = mapper.toEntity(request);
        document.setStatus(DocumentStatus.UPLOADED);
        Document saved = documentRepository.save(document);
        // ponytail: DB and RabbitMQ are not atomic; use an outbox for crash-safe delivery.
        queueSender.sendDocumentUploaded(saved.getId());
        log.info("Enqueued OCR job for document id={}", saved.getId());
        return toResponse(saved);
    }

    public DocumentResponse upload(DocumentRequest request, MultipartFile file) {
        try {
            byte[] pdf = file.getBytes();
            if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A PDF file is required");
            }
            if (file.getOriginalFilename() != null && file.getOriginalFilename().length() > 255) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File name exceeds 255 characters");
            }
            Path path = Files.createTempFile(Files.createDirectories(storagePath), "document-", ".pdf");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            log.error("Failed to clean up rolled-back upload {}", path, ex);
                        }
                    }
                }
            });
            Files.write(path, pdf);
            request.setFilePath(path.toString());
            request.setFileName(file.getOriginalFilename());
            request.setFileSize(file.getSize());
            request.setContentType("application/pdf");
            return create(request);
        } catch (IOException ex) {
            throw new DocumentServiceException("Failed to store PDF", ex);
        }
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

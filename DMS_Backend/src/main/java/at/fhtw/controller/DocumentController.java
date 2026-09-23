package at.fhtw.controller;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.service.DocumentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public ResponseEntity<List<DocumentResponse>> getAll() {
        return ResponseEntity.ok(documentService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getById(id));
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest request) {
        return ResponseEntity.ok(documentService.create(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse> update(@PathVariable Long id, @Valid @RequestBody DocumentRequest request) {
        return ResponseEntity.ok(documentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}

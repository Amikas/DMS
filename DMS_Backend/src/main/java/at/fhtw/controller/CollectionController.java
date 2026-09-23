package at.fhtw.controller;

import at.fhtw.dto.CollectionRequest;
import at.fhtw.dto.CollectionResponse;
import at.fhtw.service.CollectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/collections")
public class CollectionController {
    private final CollectionService service;

    public CollectionController(CollectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CollectionResponse> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public CollectionResponse getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public ResponseEntity<CollectionResponse> create(@Valid @RequestBody CollectionRequest request) {
        CollectionResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/collections/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    public CollectionResponse rename(@PathVariable Long id, @Valid @RequestBody CollectionRequest request) {
        return service.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/documents/{documentId}")
    public CollectionResponse addDocument(@PathVariable Long id, @PathVariable Long documentId) {
        return service.addDocument(id, documentId);
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ResponseEntity<Void> removeDocument(@PathVariable Long id, @PathVariable Long documentId) {
        service.removeDocument(id, documentId);
        return ResponseEntity.noContent().build();
    }
}

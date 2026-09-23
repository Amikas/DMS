package at.fhtw.controller;

import at.fhtw.dto.TagRequest;
import at.fhtw.dto.TagResponse;
import at.fhtw.service.TagService;
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
@RequestMapping("/api")
public class TagController {
    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    @GetMapping("/tags")
    public List<TagResponse> getAll() {
        return service.getAll();
    }

    @PostMapping("/tags")
    public ResponseEntity<TagResponse> create(@Valid @RequestBody TagRequest request) {
        TagResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/api/tags/" + response.id())).body(response);
    }

    @PutMapping("/documents/{documentId}/tags/{tagId}")
    public ResponseEntity<Void> assign(@PathVariable Long documentId, @PathVariable Long tagId) {
        service.assign(documentId, tagId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/documents/{documentId}/tags/{tagId}")
    public ResponseEntity<Void> remove(@PathVariable Long documentId, @PathVariable Long tagId) {
        service.remove(documentId, tagId);
        return ResponseEntity.noContent().build();
    }
}

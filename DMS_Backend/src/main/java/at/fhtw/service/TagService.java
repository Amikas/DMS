package at.fhtw.service;

import at.fhtw.dto.TagRequest;
import at.fhtw.dto.TagResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.Tag;
import at.fhtw.exception.ResourceNotFoundException;
import at.fhtw.repository.DocumentRepository;
import at.fhtw.repository.TagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class TagService {
    private final TagRepository tags;
    private final DocumentRepository documents;

    public TagService(TagRepository tags, DocumentRepository documents) {
        this.tags = tags;
        this.documents = documents;
    }

    public List<TagResponse> getAll() {
        return tags.findAll().stream().map(tag -> new TagResponse(tag.getId(), tag.getName())).toList();
    }

    public TagResponse create(TagRequest request) {
        String name = request.name().trim().toLowerCase(Locale.ROOT);
        if (tags.existsByName(name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tag already exists");
        }
        Tag saved = tags.save(new Tag(name));
        return new TagResponse(saved.getId(), saved.getName());
    }

    public void assign(Long documentId, Long tagId) {
        Document document = findDocument(documentId);
        Tag tag = tags.findById(tagId)
                .orElseThrow(() -> new ResourceNotFoundException("Tag not found with id: " + tagId));
        document.getTags().add(tag);
    }

    public void remove(Long documentId, Long tagId) {
        Document document = findDocument(documentId);
        document.getTags().removeIf(tag -> tag.getId().equals(tagId));
    }

    private Document findDocument(Long id) {
        return documents.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found with id: " + id));
    }
}

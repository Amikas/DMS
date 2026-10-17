package at.fhtw.mapper;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;

public interface DocumentMapper {
    Document toEntity(DocumentRequest request);

    DocumentResponse toResponse(Document document);
}

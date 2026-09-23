package at.fhtw.mapper;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ocrText", ignore = true)
    @Mapping(target = "summary", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "tags", ignore = true)
    Document toEntity(DocumentRequest request);

    @Mapping(target = "tags", ignore = true)
    DocumentResponse toResponse(Document document);
}

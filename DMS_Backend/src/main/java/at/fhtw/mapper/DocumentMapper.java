package at.fhtw.mapper;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.Instant;

@Mapper(componentModel = "spring")
public interface DocumentMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "ocrText", ignore = true)
    @Mapping(target = "summary", ignore = true)
    @Mapping(target = "summaryStartedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Document toEntity(DocumentRequest request);

    DocumentResponse toResponse(Document document);

    static DocumentStatus statusWithSearch(Document document, boolean indexed) {
        if (document.getStatus() == DocumentStatus.ARCHIVED) return DocumentStatus.ARCHIVED;
        if (document.getSummary() != null) {
            return indexed ? DocumentStatus.SUMMARY_GENERATED : DocumentStatus.PROCESSING;
        }
        // shortcut: a killed worker can leave a start event behind; expire it after the inference timeout.
        if (document.getSummaryStartedAt() != null
                && document.getSummaryStartedAt().isAfter(Instant.now().minusSeconds(720))) {
            return DocumentStatus.SUMMARIZING;
        }
        return indexed ? DocumentStatus.OCR_COMPLETE : document.getStatus();
    }
}

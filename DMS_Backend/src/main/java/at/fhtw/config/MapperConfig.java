package at.fhtw.config;

import at.fhtw.dto.DocumentRequest;
import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.entity.DocumentStatus;
import at.fhtw.mapper.DocumentMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MapperConfig {

    @Bean
    public DocumentMapper documentMapper() {
        return new DocumentMapper() {
            @Override
            public Document toEntity(DocumentRequest request) {
                Document document = new Document();
                document.setTitle(request.getTitle());
                document.setDescription(request.getDescription());
                document.setFilePath(request.getFilePath());
                document.setFileName(request.getFileName());
                document.setFileSize(request.getFileSize());
                document.setContentType(request.getContentType());
                document.setStatus(DocumentStatus.UPLOADED);
                return document;
            }

            @Override
            public DocumentResponse toResponse(Document document) {
                return DocumentResponse.builder()
                        .id(document.getId())
                        .title(document.getTitle())
                        .description(document.getDescription())
                        .filePath(document.getFilePath())
                        .fileName(document.getFileName())
                        .fileSize(document.getFileSize())
                        .contentType(document.getContentType())
                        .status(document.getStatus() != null ? document.getStatus().name() : null)
                        .ocrText(document.getOcrText())
                        .summary(document.getSummary())
                        .build();
            }
        };
    }
}

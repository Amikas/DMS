package at.fhtw.dto;

import lombok.*;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentResponse {
    private Long id;
    private String title;
    private String description;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String status;
    private String ocrText;
    private String summary;
    private List<TagResponse> tags;
}

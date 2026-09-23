package at.fhtw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentRequest {
    @NotBlank
    @Size(max = 255)
    private String title;
    private String description;
    private String filePath;
    private String fileName;
    private Long fileSize;
    private String contentType;
}

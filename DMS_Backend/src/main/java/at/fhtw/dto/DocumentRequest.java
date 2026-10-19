package at.fhtw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
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
    @Size(max = 2000)
    private String description;
    @Size(max = 255)
    private String filePath;
    @Size(max = 255)
    private String fileName;
    @PositiveOrZero
    private Long fileSize;
    @Size(max = 255)
    private String contentType;
}

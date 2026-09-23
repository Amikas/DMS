package at.fhtw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionRequest(@NotBlank @Size(max = 255) String name) {
}

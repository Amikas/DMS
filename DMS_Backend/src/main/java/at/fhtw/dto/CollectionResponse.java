package at.fhtw.dto;

import java.util.List;

public record CollectionResponse(Long id, String name, List<Long> documentIds) {
}

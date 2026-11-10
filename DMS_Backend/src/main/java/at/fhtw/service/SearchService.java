package at.fhtw.service;

import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.exception.DocumentServiceException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.DocumentRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SearchService {

    private static final int SNIPPET_LENGTH = 500;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;
    private final String index;

    public SearchService(RestClient.Builder builder,
                         ObjectMapper objectMapper,
                         DocumentRepository documentRepository,
                         DocumentMapper mapper,
                         @Value("${dms.elasticsearch.url}") String esUrl,
                         @Value("${dms.elasticsearch.index}") String index) {
        this.restClient = builder.baseUrl(esUrl).build();
        this.objectMapper = objectMapper;
        this.documentRepository = documentRepository;
        this.mapper = mapper;
        this.index = index;
    }

    public List<DocumentResponse> search(String query) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "query", Map.of("match", Map.of("content", query)),
                    "size", 50));
            String json = restClient.post()
                    .uri("/{index}/_search", index)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(json);

            List<Long> ids = new ArrayList<>();
            Map<Long, String> snippets = new HashMap<>();
            for (JsonNode hit : root.path("hits").path("hits")) {
                long id = hit.path("_id").asLong();
                ids.add(id);
                String content = hit.path("_source").path("content").asText("");
                snippets.put(id, content.substring(0, Math.min(content.length(), SNIPPET_LENGTH)));
            }
            List<Document> documents = new ArrayList<>(documentRepository.findAllById(ids));
            documents.sort(Comparator.comparingInt(document -> ids.indexOf(document.getId())));
            return documents.stream().map(document -> {
                DocumentResponse response = mapper.toResponse(document);
                response.setOcrText(snippets.get(document.getId()));
                return response;
            }).toList();
        } catch (DocumentServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new DocumentServiceException("Full-text search failed", ex);
        }
    }
}

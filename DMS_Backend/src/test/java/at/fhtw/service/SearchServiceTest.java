package at.fhtw.service;

import at.fhtw.dto.DocumentResponse;
import at.fhtw.entity.Document;
import at.fhtw.exception.DocumentServiceException;
import at.fhtw.mapper.DocumentMapper;
import at.fhtw.repository.DocumentRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMapper mapper;

    private SearchService service;
    private MockRestServiceServer server;

    private void setUpService() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SearchService(builder, new ObjectMapper(), documentRepository, mapper,
                "http://es:9200", "documents");
    }

    @Test
    void search_shouldReturnDocumentsInElasticsearchOrder() {
        setUpService();
        Document doc1 = new Document();
        doc1.setId(1L);
        doc1.setTitle("HelloWorld");
        Document doc2 = new Document();
        doc2.setId(2L);
        doc2.setTitle("Other");
        when(documentRepository.findAllById(List.of(2L, 1L))).thenReturn(List.of(doc2, doc1));
        when(mapper.toResponse(doc1)).thenReturn(DocumentResponse.builder().id(1L).title("HelloWorld").build());
        when(mapper.toResponse(doc2)).thenReturn(DocumentResponse.builder().id(2L).title("Other").build());

        server.expect(requestTo("http://es:9200/documents/_search"))
                .andRespond(withSuccess("""
                        {"hits":{"hits":[
                          {"_id":"2","_source":{"content":"hello there"}},
                          {"_id":"1","_source":{"content":"Hello World"}}
                        ]}}
                        """, MediaType.APPLICATION_JSON));

        List<DocumentResponse> results = service.search("Hello");

        assertEquals(2, results.size());
        assertEquals(2L, results.get(0).getId());
        assertEquals("hello there", results.get(0).getOcrText());
        assertEquals(1L, results.get(1).getId());
        assertEquals("Hello World", results.get(1).getOcrText());
    }

    @Test
    void search_shouldReturnEmptyList_whenNoHits() {
        setUpService();

        server.expect(requestTo("http://es:9200/documents/_search"))
                .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}", MediaType.APPLICATION_JSON));

        List<DocumentResponse> results = service.search("nomatch");

        assertTrue(results.isEmpty());
    }

    @Test
    void search_shouldThrow_whenElasticsearchIsDown() {
        setUpService();

        server.expect(requestTo("http://es:9200/documents/_search"))
                .andRespond(withServerError());

        assertThrows(DocumentServiceException.class, () -> service.search("Hello"));
    }
}

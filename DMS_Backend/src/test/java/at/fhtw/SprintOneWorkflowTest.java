package at.fhtw;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import at.fhtw.entity.Document;
import at.fhtw.repository.DocumentRepository;
import at.fhtw.service.DocumentSummaryReceiver;
import at.fhtw.service.SearchService;

import java.time.Instant;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprint1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class SprintOneWorkflowTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper json;
    @Autowired private DocumentRepository documents;
    @Autowired private DocumentSummaryReceiver summaries;
    @MockitoBean private RabbitTemplate rabbit;
    @MockitoBean private S3Client storageClient;
    // This REST/database test does not start an external Elasticsearch instance.
    @MockitoBean private SearchService searchService;

    @Test
    void showsSummaryProgressOnlyForActiveWorkAndCompletesOnceIndexed() throws Exception {
        var document = documents.saveAndFlush(Document.builder().title("PDF").filePath("test.pdf").build());
        long id = document.getId();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        when(searchService.getOcrText(id)).thenReturn("Extracted text");
        assertEquals("OCR_COMPLETE", documentDetails(mvc, id).get("status").asText());

        summaries.receive(new Message(new byte[]{'{'}));
        summaryEvent(id, "old.pdf", Map.of("summarizing", true));
        assertEquals("OCR_COMPLETE", documentDetails(mvc, id).get("status").asText());
        summaryEvent(id, "test.pdf", Map.of("summarizing", true));
        assertEquals("SUMMARIZING", documentDetails(mvc, id).get("status").asText());
        summaryEvent(id, "test.pdf", Map.of("summarizing", false));
        assertEquals("OCR_COMPLETE", documentDetails(mvc, id).get("status").asText());

        document.setSummaryStartedAt(Instant.now().minusSeconds(721));
        documents.saveAndFlush(document);
        assertEquals("OCR_COMPLETE", documentDetails(mvc, id).get("status").asText());
        summaryEvent(id, "old.pdf", Map.of("summary", "Stale"));
        assertNull(documents.findById(id).orElseThrow().getSummary());

        String summary = "Partial summary: Opening paragraph.";
        summaryEvent(id, "test.pdf", Map.of("summarizing", true));
        summaryEvent(id, "test.pdf", Map.of("summary", summary));
        summaryEvent(id, "test.pdf", Map.of("summary", "Duplicate"));
        summaryEvent(id, "test.pdf", Map.of("summarizing", true));
        when(searchService.getOcrText(id)).thenReturn(null);
        assertEquals("PROCESSING", documentDetails(mvc, id).get("status").asText());
        when(searchService.getOcrText(id)).thenReturn("Extracted text");
        JsonNode details = documentDetails(mvc, id);
        assertEquals("SUMMARY_GENERATED", details.get("status").asText());
        assertEquals(summary, details.get("summary").asText());
        assertNull(documents.findById(id).orElseThrow().getSummaryStartedAt());
    }

    private JsonNode documentDetails(MockMvc mvc, long id) throws Exception {
        return json.readTree(mvc.perform(get("/api/documents/{id}", id))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void summaryEvent(long id, String objectKey, Map<String, Object> event) {
        var message = new java.util.HashMap<>(event);
        message.put("documentId", id);
        message.put("objectKey", objectKey);
        summaries.receive(new Message(json.writeValueAsBytes(message)));
    }

    @Test
    void uploadsPdfAndRemovesFailedUploadsOnRollback() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes();
        var file = new MockMultipartFile("file", "hello.pdf", "application/pdf", pdf);
        JsonNode uploaded = json.readTree(mvc.perform(multipart("/api/documents").file(file)
                        .param("title", "PDF").param("filePath", "/untrusted/path"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String objectKey = uploaded.get("filePath").asText();
        ArgumentCaptor<PutObjectRequest> putArgs = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(storageClient).putObject(putArgs.capture(), any(RequestBody.class));
        assertEquals(objectKey, putArgs.getValue().key());
        assertEquals("documents", putArgs.getValue().bucket());
        assertEquals(pdf.length, uploaded.get("fileSize").asInt());
        verify(rabbit).convertAndSend(anyString(), anyString(),
                contains("\"documentId\":" + uploaded.get("id").asLong() + ","));

        long count = documents.count();
        doThrow(new AmqpException("offline")).when(rabbit)
                .convertAndSend(anyString(), anyString(), anyString());
        mvc.perform(multipart("/api/documents").file(file).param("title", "Rollback"))
                .andExpect(status().isInternalServerError());
        assertEquals(count, documents.count());
        verify(storageClient, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void rejectsBadRequestsWithoutTreatingThemAsServerFailures() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/documents/not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents").param("title", "Not PDF")
                        .file(new MockMultipartFile("file", "fake.pdf", "application/pdf", "not PDF".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/documents").param("title", "Empty")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0])))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(rabbit);
    }

    @Test
    void documentsAndCollectionsPersistThroughRestApi() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JsonNode document = json.readTree(mvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Report\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long documentId = document.get("id").asLong();

        JsonNode collection = json.readTree(mvc.perform(post("/api/collections")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Coursework\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long collectionId = collection.get("id").asLong();
        mvc.perform(put("/api/collections/{id}/documents/{documentId}", collectionId, documentId))
                .andExpect(status().isOk());
        mvc.perform(put("/api/collections/{id}/documents/{documentId}", collectionId, documentId))
                .andExpect(status().isOk());

        JsonNode grouped = json.readTree(mvc.perform(get("/api/collections/{id}", collectionId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, grouped.get("documentIds").size());
        assertEquals(documentId, grouped.get("documentIds").get(0).asLong());

        mvc.perform(delete("/api/collections/{id}", collectionId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", documentId)).andExpect(status().isOk());
        mvc.perform(delete("/api/documents/{id}", documentId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", documentId)).andExpect(status().isNotFound());
    }
}

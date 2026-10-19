package at.fhtw;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import at.fhtw.repository.DocumentRepository;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprint1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SprintOneWorkflowTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper json;
    @Autowired private DocumentRepository documents;
    @MockitoBean private RabbitTemplate rabbit;
    @TempDir static Path uploads;

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry properties) {
        properties.add("dms.storage-path", () -> uploads.toString());
    }

    @Test
    void uploadsPdfAndRemovesFailedUploadsOnRollback() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        byte[] pdf = "%PDF-1.4\n%%EOF\n".getBytes();
        var file = new MockMultipartFile("file", "hello.pdf", "application/pdf", pdf);
        JsonNode uploaded = json.readTree(mvc.perform(multipart("/api/documents").file(file)
                        .param("title", "PDF").param("filePath", "/untrusted/path"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        Path stored = Path.of(uploaded.get("filePath").asText());
        assertEquals(uploads, stored.getParent());
        assertArrayEquals(pdf, Files.readAllBytes(stored));
        assertEquals(pdf.length, uploaded.get("fileSize").asInt());
        verify(rabbit).convertAndSend(anyString(), anyString(),
                contains("\"documentId\":" + uploaded.get("id").asLong() + ","));

        long count = documents.count();
        doThrow(new AmqpException("offline")).when(rabbit)
                .convertAndSend(anyString(), anyString(), anyString());
        mvc.perform(multipart("/api/documents").file(file).param("title", "Rollback"))
                .andExpect(status().isInternalServerError());
        assertEquals(count, documents.count());
        try (var files = Files.list(uploads)) {
            assertEquals(1, files.count());
        }
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

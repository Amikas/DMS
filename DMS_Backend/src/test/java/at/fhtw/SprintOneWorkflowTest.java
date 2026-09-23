package at.fhtw;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sprint1;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class SprintOneWorkflowTest {
    @Autowired private WebApplicationContext context;
    @Autowired private ObjectMapper json;

    @Test
    void documentsTagsAndCollectionsPersistThroughRestApi() throws Exception {
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
        JsonNode document = json.readTree(mvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Report\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long documentId = document.get("id").asLong();

        JsonNode tag = json.readTree(mvc.perform(post("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" Work \"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long tagId = tag.get("id").asLong();
        assertEquals("work", tag.get("name").asText());
        mvc.perform(post("/api/tags").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"work\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/documents/{documentId}/tags/{tagId}", documentId, tagId))
                .andExpect(status().isNoContent());

        JsonNode tagged = json.readTree(mvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(tagId, tagged.get("tags").get(0).get("id").asLong());

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
        assertTrue(json.readTree(mvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("tags").isArray());
        mvc.perform(delete("/api/documents/{id}/tags/{tagId}", documentId, tagId))
                .andExpect(status().isNoContent());
        JsonNode untagged = json.readTree(mvc.perform(get("/api/documents/{id}", documentId))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(0, untagged.get("tags").size());
        mvc.perform(delete("/api/documents/{id}", documentId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", documentId)).andExpect(status().isNotFound());
    }
}

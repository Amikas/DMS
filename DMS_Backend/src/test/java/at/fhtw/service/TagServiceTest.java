package at.fhtw.service;

import at.fhtw.dto.TagRequest;
import at.fhtw.entity.Document;
import at.fhtw.entity.Tag;
import at.fhtw.repository.DocumentRepository;
import at.fhtw.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagServiceTest {
    @Mock private TagRepository tags;
    @Mock private DocumentRepository documents;
    @InjectMocks private TagService service;

    @Test
    void duplicateTagNameIsRejectedAfterNormalization() {
        when(tags.existsByName("work")).thenReturn(true);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(new TagRequest(" Work ")));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(tags, never()).save(any());
    }

    @Test
    void tagCanBeAssignedAndRemoved() {
        Document document = new Document();
        Tag tag = mock(Tag.class);
        when(tag.getId()).thenReturn(2L);
        when(documents.findById(1L)).thenReturn(Optional.of(document));
        when(tags.findById(2L)).thenReturn(Optional.of(tag));

        service.assign(1L, 2L);
        assertTrue(document.getTags().contains(tag));

        service.remove(1L, 2L);
        assertTrue(document.getTags().isEmpty());
    }
}

package at.fhtw.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ObjectStorageServiceTest {

    @Test
    void putObject_withoutClient_throwsDocumentServiceException() {
        ObjectStorageService service = new ObjectStorageService();
        ReflectionTestUtils.setField(service, "bucket", "test");
        assertThrows(Exception.class,
                () -> service.putObject("k.pdf", new byte[]{1}, "application/pdf"));
    }
}

package at.fhtw.service;

import at.fhtw.exception.DocumentServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ObjectStorageServiceTest {

    @Mock
    private S3Client storageClient;

    private ObjectStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new ObjectStorageService(storageClient, "documents");
    }

    @Test
    void store_shouldPutObjectInBucket() throws Exception {
        storage.store("key.pdf", "pdf".getBytes());

        verify(storageClient).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void store_shouldCreateBucketOnFirstUse() throws Exception {
        when(storageClient.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).build());
        storage.store("key.pdf", "pdf".getBytes());

        verify(storageClient).createBucket(CreateBucketRequest.builder().bucket("documents").build());
    }

    @Test
    void store_shouldNotCreateBucket_whenAccessIsDenied() {
        when(storageClient.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).build());

        assertThrows(DocumentServiceException.class, () -> storage.store("key.pdf", "pdf".getBytes()));
        verify(storageClient, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void store_shouldThrow_whenObjectStorageFails() throws Exception {
        doThrow(new RuntimeException("offline")).when(storageClient)
                .putObject(any(PutObjectRequest.class), any(RequestBody.class));

        assertThrows(DocumentServiceException.class, () -> storage.store("key.pdf", "pdf".getBytes()));
    }

    @Test
    void remove_shouldDeleteObjectFromBucket() throws Exception {
        storage.remove("key.pdf");

        verify(storageClient).deleteObject(DeleteObjectRequest.builder().bucket("documents").key("key.pdf").build());
    }
}

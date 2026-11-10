package at.fhtw.service;

import at.fhtw.exception.DocumentServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {

    private final S3Client client;
    private final String bucket;
    private volatile boolean bucketReady;

    public ObjectStorageService(S3Client client, @Value("${dms.storage.bucket}") String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    // Creates the bucket on first use so startup does not depend on object storage being up.
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            try {
                try {
                    client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                } catch (S3Exception ex) {
                    if (ex.statusCode() != 404) {
                        throw ex;
                    }
                    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw new DocumentServiceException("Failed to prepare object storage bucket", ex);
            }
        }
    }

    public void store(String key, byte[] content) {
        ensureBucket();
        try {
            client.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/pdf")
                    .build(), RequestBody.fromBytes(content));
        } catch (Exception ex) {
            throw new DocumentServiceException("Failed to store PDF in object storage", ex);
        }
    }

    public void remove(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (Exception ex) {
            throw new DocumentServiceException("Failed to remove PDF from object storage", ex);
        }
    }
}

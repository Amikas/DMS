package at.fhtw.service;

import at.fhtw.exception.DocumentServiceException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

@Service
@Slf4j

public class ObjectStorageService {

    @Value("${dms.storage.endpoint}")
    private String endpoint;

    @Value("${dms.storage.bucket}")
    private String bucket;

    @Value("${dms.storage.access-key}")
    private String accessKey;

    @Value("${dms.storage.secret-key}")
    private String secretKey;

    private MinioClient client;

    @PostConstruct
    void init() {
        client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket();
    }

    void ensureBucket() {
        try {
            boolean exists = client.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created bucket '{}'", bucket);
            } else {
                log.info("Bucket '{}' already exists", bucket);
            }
        } catch (Exception e) {
            throw new DocumentServiceException(
                    "Could not ensure bucket " + bucket, e);
        }
    }

    public String putObject(String key, byte[] data, String contentType) {
        try {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            log.info("Stored object '{}' ({} bytes)", key, data.length);
            return key;
        } catch (Exception e) {
            throw new DocumentServiceException("Failed to store object " + key, e);
        }
    }

    public void deleteObject(String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .build());
            log.info("Deleted object '{}'", key);
        } catch (Exception e) {
            throw new DocumentServiceException("Failed to delete object " + key, e);
        }
    }
}

package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;

/**
 * One S3 client for the process: the pipeline downloads and uploads, and the
 * bucket initialiser creates the two buckets. Sharing the client keeps the
 * endpoint and credentials logic in one place.
 */
@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(WorkerProperties properties) {
        WorkerProperties.Storage storage = properties.storage();
        return S3Client.builder()
            .endpointOverride(URI.create(storage.endpoint()))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey())))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(storage.pathStyle())
                .build())
            .build();
    }
}

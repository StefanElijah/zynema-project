package dev.zynema.playback.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Storage access for the stream endpoints: one client to read manifests, one
 * presigner to sign segment URLs. Presigning is offline — it computes a
 * signature, it does not call MinIO.
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public S3Client playbackStorageClient(StorageProperties properties) {
        return S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build())
            .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties properties) {
        return S3Presigner.builder()
            .endpointOverride(URI.create(properties.presignEndpointOrDefault()))
            .region(Region.US_EAST_1)
            .credentialsProvider(credentials(properties))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(properties.pathStyle())
                .build())
            .build();
    }

    private StaticCredentialsProvider credentials(StorageProperties properties) {
        return StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
    }
}

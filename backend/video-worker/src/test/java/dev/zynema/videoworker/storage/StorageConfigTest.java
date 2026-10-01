package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The single S3 client must point at the compose endpoint with path-style
 * access, because MinIO does not do virtual-host addressing.
 */
class StorageConfigTest {

    @Test
    void buildsAClientForTheConfiguredEndpoint() {
        WorkerProperties properties = mock(WorkerProperties.class);
        WorkerProperties.Storage storage = mock(WorkerProperties.Storage.class);
        when(properties.storage()).thenReturn(storage);
        when(storage.endpoint()).thenReturn("http://minio:9000");
        when(storage.accessKey()).thenReturn("zynema");
        when(storage.secretKey()).thenReturn("zynema-secret");
        when(storage.pathStyle()).thenReturn(true);

        try (S3Client client = new StorageConfig().s3Client(properties)) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://minio:9000"));
            assertThat(client.serviceClientConfiguration().region()).isEqualTo(Region.US_EAST_1);
        }
    }
}

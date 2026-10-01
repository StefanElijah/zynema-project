package dev.zynema.videoworker.storage;

import dev.zynema.videoworker.config.WorkerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The initialiser runs right after MinIO starts, so it must create what is
 * missing, leave what exists alone and retry until the server answers.
 */
class BucketInitializerTest {

    private final S3Client client = mock(S3Client.class);
    private final WorkerProperties properties = mock(WorkerProperties.class);
    private final WorkerProperties.Storage storage = mock(WorkerProperties.Storage.class);

    private final BucketInitializer initializer = new BucketInitializer(client, properties);

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void createsTheMissingBucketAndSkipsTheExistingOne() {
        givenBuckets();
        when(client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(NoSuchBucketException.builder().build())
            .thenReturn(HeadBucketResponse.builder().build());

        initializer.initialize();

        ArgumentCaptor<CreateBucketRequest> created = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(client).createBucket(created.capture());
        assertThat(created.getValue().bucket()).isEqualTo("zynema-sources");
    }

    @Test
    void leavesExistingBucketsAlone() {
        givenBuckets();
        when(client.headBucket(any(HeadBucketRequest.class)))
            .thenReturn(HeadBucketResponse.builder().build());

        initializer.initialize();

        verify(client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void retriesUntilTheServerAnswers() {
        givenBuckets();
        when(client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(new RuntimeException("connection refused"))
            .thenReturn(HeadBucketResponse.builder().build());

        assertThatCode(initializer::initialize).doesNotThrowAnyException();
    }

    @Test
    void failsFastWhenTheWaitIsInterrupted() {
        givenBuckets();
        when(client.headBucket(any(HeadBucketRequest.class)))
            .thenThrow(new RuntimeException("connection refused"));
        Thread.currentThread().interrupt();

        assertThatThrownBy(initializer::initialize)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Interrupted while waiting for storage");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    private void givenBuckets() {
        when(properties.storage()).thenReturn(storage);
        when(storage.sourceBucket()).thenReturn("zynema-sources");
        when(storage.hlsBucket()).thenReturn("zynema-hls");
    }
}

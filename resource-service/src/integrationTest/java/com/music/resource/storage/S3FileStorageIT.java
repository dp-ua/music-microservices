package com.music.resource.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.music.resource.configuration.S3Config;
import com.music.resource.exception.StorageException;

import software.amazon.awssdk.services.s3.S3Client;

/**
 * The storage adapter against a real S3 API (LocalStack, the same image as in the docker compose stack).
 * The client is built by the production {@link S3Config}, so the endpoint, region and path style settings are
 * checked too.
 */
@Testcontainers
class S3FileStorageIT {

    private static final String BUCKET = "resources-it";
    private static final String MISSING_BUCKET = "no-such-bucket";

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8.1"))
            .withServices(LocalStackContainer.Service.S3);

    private static S3Client s3Client;
    private static S3FileStorage storage;
    private static byte[] mp3;

    @BeforeAll
    static void setUp() throws IOException {
        var properties = properties(BUCKET);
        s3Client = new S3Config().s3Client(properties);
        s3Client.createBucket(request -> request.bucket(BUCKET));
        storage = new S3FileStorage(s3Client, properties);

        try (var stream = S3FileStorageIT.class.getResourceAsStream("/valid-sample-with-required-tags.mp3")) {
            assertThat(stream).as("sample file on the classpath").isNotNull();
            mp3 = stream.readAllBytes();
        }
    }

    @AfterAll
    static void tearDown() {
        s3Client.close();
    }

    @Test
    @DisplayName("A file is downloaded exactly as it was uploaded")
    void uploadThenDownloadReturnsSameBytes() {
        var location = storage.upload(mp3);

        var downloaded = storage.download(location);

        assertThat(downloaded).isEqualTo(mp3);
    }

    @Test
    @DisplayName("The location points to the configured bucket, the key is a new .mp3 name every time")
    void uploadReturnsUniqueLocations() {
        var first = storage.upload(mp3);
        var second = storage.upload(mp3);

        assertThat(first.getBucket()).isEqualTo(BUCKET);
        assertThat(first.getKey()).endsWith(".mp3");
        assertThat(second.getKey()).isNotEqualTo(first.getKey());
    }

    @Test
    @DisplayName("The object is stored with the audio/mpeg content type")
    void uploadStoresContentType() {
        var location = storage.upload(mp3);

        var metadata = s3Client.headObject(request -> request.bucket(location.getBucket()).key(location.getKey()));

        assertThat(metadata.contentType()).isEqualTo("audio/mpeg");
        assertThat(metadata.contentLength()).isEqualTo(mp3.length);
    }

    @Test
    @DisplayName("A deleted file can not be downloaded any more")
    void deletedFileIsNotFound() {
        var location = storage.upload(mp3);

        storage.delete(location);

        assertThatThrownBy(() -> storage.download(location))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getErrorCode()).isEqualTo("404"))
                .hasMessageContaining(location.getKey());
    }

    @Test
    @DisplayName("Deleting the same file twice is not an error")
    void deleteIsIdempotent() {
        var location = storage.upload(mp3);
        storage.delete(location);

        assertThatCode(() -> storage.delete(location)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Downloading a file that never existed is a 404")
    void downloadOfUnknownKeyIsNotFound() {
        var unknown = StorageLocation.builder().bucket(BUCKET).key("never-uploaded.mp3").build();

        assertThatThrownBy(() -> storage.download(unknown))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getErrorCode()).isEqualTo("404"));
    }

    @Test
    @DisplayName("A storage problem (the bucket does not exist) is a 500, not a 404")
    void missingBucketIsServerError() {
        var storageWithoutBucket = new S3FileStorage(s3Client, properties(MISSING_BUCKET));

        assertThatThrownBy(() -> storageWithoutBucket.upload(mp3))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"))
                .hasMessageContaining("cannot upload");

        var location = StorageLocation.builder().bucket(MISSING_BUCKET).key("any.mp3").build();
        assertThatThrownBy(() -> storage.download(location))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"));
    }

    private static S3Properties properties(String bucket) {
        var properties = new S3Properties();
        properties.setEndpoint(LOCALSTACK.getEndpoint().toString());
        properties.setRegion(LOCALSTACK.getRegion());
        properties.setAccessKey(LOCALSTACK.getAccessKey());
        properties.setSecretKey(LOCALSTACK.getSecretKey());
        properties.setBucket(bucket);
        properties.setPathStyleAccess(true);
        return properties;
    }

}

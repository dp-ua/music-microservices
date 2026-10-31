package com.music.resource.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.music.resource.model.AudioResource;

import jakarta.persistence.EntityManager;

/**
 * The repository against a real PostgreSQL. The schema is created by the same script the docker compose stack uses
 * (init-scripts/resource-db/init.sql), so the test cannot drift from the real database.
 */
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ResourcesRepositoryIT {

    private static final Path INIT_SCRIPT = Path.of("..", "init-scripts", "resource-db", "init.sql")
            .toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPT), "/docker-entrypoint-initdb.d/init.sql");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    /**
     * Only the JPA part of the application: the full application class also starts Feign and Eureka.
     */
    @SpringBootConfiguration
    @EntityScan(basePackageClasses = AudioResource.class)
    @EnableJpaRepositories(basePackageClasses = ResourcesRepository.class)
    static class JpaOnlyConfiguration {

    }

    @Autowired
    private ResourcesRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("A saved resource gets a generated id and both timestamps")
    void saveGeneratesIdAndTimestamps() {
        var saved = repository.saveAndFlush(resource("key-1.mp3", 1_024L));

        assertThat(saved.getId()).isNotNull().isPositive();
        assertThat(saved.getCreatedAt()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
        assertThat(saved.getUpdatedAt()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("A resource is read back from the database with all its fields")
    void findByIdReturnsSavedFields() {
        var id = repository.saveAndFlush(resource("key-2.mp3", 2_048L)).getId();
        entityManager.clear();

        var found = repository.findById(id);

        assertThat(found).isPresent();
        assertThat(found.get().getStorageBucket()).isEqualTo("resources");
        assertThat(found.get().getStorageKey()).isEqualTo("key-2.mp3");
        assertThat(found.get().getFileSize()).isEqualTo(2_048L);
        assertThat(found.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("An unknown id is not found")
    void findByIdOfUnknownResourceIsEmpty() {
        assertThat(repository.findById(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("findAllById returns only the existing resources, unknown ids are skipped")
    void findAllByIdSkipsUnknownIds() {
        var first = repository.saveAndFlush(resource("key-3.mp3", 1L));
        var second = repository.saveAndFlush(resource("key-4.mp3", 2L));
        entityManager.clear();

        var found = repository.findAllById(List.of(first.getId(), second.getId(), Long.MAX_VALUE));

        assertThat(found).extracting(AudioResource::getId).containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("deleteAllInBatch removes the given resources and keeps the others")
    void deleteAllInBatchRemovesOnlyGivenResources() {
        var toDelete = repository.saveAndFlush(resource("key-5.mp3", 1L));
        var alsoDelete = repository.saveAndFlush(resource("key-6.mp3", 2L));
        var toKeep = repository.saveAndFlush(resource("key-7.mp3", 3L));

        repository.deleteAllInBatch(List.of(toDelete, alsoDelete));
        entityManager.clear();

        assertThat(repository.findById(toDelete.getId())).isEmpty();
        assertThat(repository.findById(alsoDelete.getId())).isEmpty();
        assertThat(repository.findById(toKeep.getId())).isPresent();
    }

    @Test
    @DisplayName("An update moves updated_at forward and never touches created_at")
    void updateChangesUpdatedAtButNotCreatedAt() throws InterruptedException {
        var id = repository.saveAndFlush(resource("key-8.mp3", 1L)).getId();
        entityManager.clear();
        var before = repository.findById(id).orElseThrow();
        var createdAt = before.getCreatedAt();
        var updatedAt = before.getUpdatedAt();
        Thread.sleep(20);

        before.setFileSize(999L);
        repository.saveAndFlush(before);
        entityManager.clear();
        var after = repository.findById(id).orElseThrow();

        assertThat(after.getFileSize()).isEqualTo(999L);
        assertThat(after.getCreatedAt()).isEqualTo(createdAt);
        assertThat(after.getUpdatedAt()).isAfter(updatedAt);
    }

    @Test
    @DisplayName("A resource without the storage location is rejected, the table keeps its NOT NULL rules")
    void resourceWithoutLocationIsRejected() {
        var invalid = AudioResource.builder().fileSize(1L).build();

        assertThatThrownBy(() -> repository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static AudioResource resource(String key, long size) {
        return AudioResource.builder()
                .storageBucket("resources")
                .storageKey(key)
                .fileSize(size)
                .build();
    }

}

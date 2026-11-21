package com.music.storage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.exception.InvalidIdException;
import com.music.storage.exception.StorageAlreadyExistsException;
import com.music.storage.mapper.StorageMapperImpl;
import com.music.storage.model.Storage;
import com.music.storage.model.StorageType;
import com.music.storage.repository.StorageRepository;
import com.music.storage.validator.CSVValidator;

/**
 * Business logic of storage-service. The repository is a mock, the mapper (MapStruct) and the id validator are real.
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    @Mock
    private StorageRepository repository;

    private StorageServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StorageServiceImpl(repository, new StorageMapperImpl(), new CSVValidator());
    }

    @Test
    @DisplayName("A new storage is saved and its id is returned")
    void createsStorage() {
        var dto = new StorageCreateDto(StorageType.PERMANENT, "bucket_name", "/files");
        when(repository.existsByBucketAndPath("bucket_name", "/files")).thenReturn(false);
        when(repository.save(any(Storage.class))).thenAnswer(call -> {
            Storage saved = call.getArgument(0);
            saved.setId(11L);
            return saved;
        });

        var result = service.createStorage(dto);

        assertThat(result.getId()).isEqualTo(11L);
        var captor = ArgumentCaptor.forClass(Storage.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getStorageType()).isEqualTo(StorageType.PERMANENT);
        assertThat(captor.getValue().getBucket()).isEqualTo("bucket_name");
        assertThat(captor.getValue().getPath()).isEqualTo("/files");
    }

    @Test
    @DisplayName("A bucket and path that are already used are rejected with 400")
    void duplicateIsRejected() {
        var dto = new StorageCreateDto(StorageType.STAGING, "staging-bucket", "/files");
        when(repository.existsByBucketAndPath("staging-bucket", "/files")).thenReturn(true);

        assertThatThrownBy(() -> service.createStorage(dto))
                .isInstanceOfSatisfying(StorageAlreadyExistsException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("400"));
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("All storages are returned with id, type, bucket and path")
    void returnsAllStorages() {
        when(repository.findAll()).thenReturn(List.of(
                Storage.builder().id(1L).storageType(StorageType.STAGING).bucket("s").path("/files").build(),
                Storage.builder().id(2L).storageType(StorageType.PERMANENT).bucket("p").path("/files").build()));

        var result = service.getAllStorages();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getStorageType()).isEqualTo(StorageType.STAGING);
        assertThat(result.get(1).getBucket()).isEqualTo("p");
    }

    @Test
    @DisplayName("Only the existing storages are deleted, the other ids are ignored")
    void deletesExistingStoragesOnly() {
        var existing = Storage.builder().id(1L).storageType(StorageType.STAGING).bucket("s").path("/f").build();
        when(repository.findAllById(List.of(1L, 999L))).thenReturn(List.of(existing));

        var result = service.deleteStorages("1,999");

        assertThat(result.getIds()).containsExactly(1L);
        verify(repository).deleteAllInBatch(List.of(existing));
    }

    @Test
    @DisplayName("An invalid CSV is rejected with 400 and nothing is deleted")
    void invalidCsvIsRejected() {
        assertThatThrownBy(() -> service.deleteStorages("1,abc"))
                .isInstanceOfSatisfying(InvalidIdException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo("400"));
        assertThatThrownBy(() -> service.deleteStorages("1".repeat(201)))
                .isInstanceOf(InvalidIdException.class)
                .hasMessageContaining("too long");
        verify(repository, never()).deleteAllInBatch(any());
    }

}

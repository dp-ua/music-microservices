package com.music.resource.client.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.resource.exception.StorageException;

@ExtendWith(MockitoExtension.class)
class StorageRegistryTest {

    @Mock
    private StorageServiceClientProxy client;
    @InjectMocks
    private StorageRegistry registry;

    @Test
    @DisplayName("The storage of the requested type is picked from the list")
    void findsStorageByType() {
        var staging = new StorageDto(1L, StorageType.STAGING, "s", "/files");
        var permanent = new StorageDto(2L, StorageType.PERMANENT, "p", "/files");
        when(client.getStorages()).thenReturn(List.of(staging, permanent));

        assertThat(registry.get(StorageType.PERMANENT)).isSameAs(permanent);
        assertThat(registry.get(StorageType.STAGING)).isSameAs(staging);
    }

    @Test
    @DisplayName("If there are several storages of the type, the first one is used")
    void firstOfSeveralIsUsed() {
        var first = new StorageDto(1L, StorageType.STAGING, "a", "/files");
        var second = new StorageDto(3L, StorageType.STAGING, "b", "/files");
        when(client.getStorages()).thenReturn(List.of(first, second));

        assertThat(registry.get(StorageType.STAGING)).isSameAs(first);
    }

    @Test
    @DisplayName("A type without a storage is a 500: the service is not configured")
    void missingTypeIsServerError() {
        when(client.getStorages()).thenReturn(List.of());

        assertThatThrownBy(() -> registry.get(StorageType.STAGING))
                .isInstanceOfSatisfying(StorageException.class, e -> assertThat(e.getErrorCode()).isEqualTo("500"))
                .hasMessageContaining("STAGING");
    }

}

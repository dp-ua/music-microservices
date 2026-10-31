package com.music.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.resource.client.song.SongServiceClientProxy;
import com.music.resource.exception.InvalidIdException;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;
import com.music.resource.exception.StorageException;
import com.music.resource.mapper.ResourceMapperImpl;
import com.music.resource.messaging.ResourceEventPublisher;
import com.music.resource.model.AudioResource;
import com.music.resource.repository.ResourcesRepository;
import com.music.resource.storage.FileStorage;
import com.music.resource.storage.StorageLocation;
import com.music.resource.validator.CSVValidator;

/**
 * Business logic of resource-service. Storage, database, broker and song-service are mocked, the mapper
 * (MapStruct) and the id validator are real.
 */
@ExtendWith(MockitoExtension.class)
class ResourcesServiceImplTest {

    private static final byte[] FILE = {10, 20, 30, 40};
    private static final StorageLocation LOCATION = StorageLocation.builder().bucket("resources").key("a.mp3").build();

    @Mock
    private SongServiceClientProxy songServiceClientProxy;
    @Mock
    private ResourcesRepository resourcesRepository;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ResourceEventPublisher eventPublisher;

    private ResourcesServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourcesServiceImpl(
                songServiceClientProxy,
                new ResourceMapperImpl(),
                resourcesRepository,
                fileStorage,
                eventPublisher,
                new CSVValidator());
    }

    @Nested
    @DisplayName("Upload")
    class Upload {

        @Test
        @DisplayName("The file goes to storage, the location to the database, then the event is published")
        void storesFileThenSavesLocationThenPublishesEvent() {
            when(fileStorage.upload(FILE)).thenReturn(LOCATION);
            when(resourcesRepository.save(any(AudioResource.class))).thenReturn(resource(7L, "a.mp3"));

            var result = service.uploadResource(FILE);

            assertThat(result.getId()).isEqualTo(7L);
            var order = inOrder(fileStorage, resourcesRepository, eventPublisher);
            order.verify(fileStorage).upload(FILE);
            order.verify(resourcesRepository).save(any(AudioResource.class));
            order.verify(eventPublisher).publishUploaded(7L);
        }

        @Test
        @DisplayName("Only the location and the size are saved in the database, not the file")
        void savesLocationAndSize() {
            when(fileStorage.upload(FILE)).thenReturn(LOCATION);
            when(resourcesRepository.save(any(AudioResource.class))).thenReturn(resource(7L, "a.mp3"));

            service.uploadResource(FILE);

            var captor = ArgumentCaptor.forClass(AudioResource.class);
            verify(resourcesRepository).save(captor.capture());
            assertThat(captor.getValue().getStorageBucket()).isEqualTo("resources");
            assertThat(captor.getValue().getStorageKey()).isEqualTo("a.mp3");
            assertThat(captor.getValue().getFileSize()).isEqualTo(FILE.length);
            assertThat(captor.getValue().getId()).isNull();
        }

        @Test
        @DisplayName("If the database fails, the file is removed from storage and nothing is published")
        void databaseFailureRemovesFileAndPublishesNothing() {
            var dbError = new IllegalStateException("db is down");
            when(fileStorage.upload(FILE)).thenReturn(LOCATION);
            when(resourcesRepository.save(any(AudioResource.class))).thenThrow(dbError);

            assertThatThrownBy(() -> service.uploadResource(FILE)).isSameAs(dbError);

            verify(fileStorage).delete(LOCATION);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("If the cleanup fails too, the original database error is thrown and the cleanup error is kept")
        void cleanupFailureIsSuppressed() {
            var dbError = new IllegalStateException("db is down");
            var cleanupError = StorageException.failed("delete", "a.mp3", null);
            when(fileStorage.upload(FILE)).thenReturn(LOCATION);
            when(resourcesRepository.save(any(AudioResource.class))).thenThrow(dbError);
            doThrow(cleanupError).when(fileStorage).delete(LOCATION);

            assertThatThrownBy(() -> service.uploadResource(FILE))
                    .isSameAs(dbError)
                    .hasSuppressedException(cleanupError);
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("If the storage fails, nothing is saved and nothing is published")
        void storageFailureStopsUpload() {
            var storageError = StorageException.failed("upload", "a.mp3", null);
            when(fileStorage.upload(FILE)).thenThrow(storageError);

            assertThatThrownBy(() -> service.uploadResource(FILE)).isSameAs(storageError);

            verifyNoInteractions(resourcesRepository, eventPublisher);
        }

        @Test
        @DisplayName("An empty body is rejected with 400 before anything is stored")
        void emptyBodyIsRejected() {
            assertThatThrownBy(() -> service.uploadResource(new byte[0]))
                    .isInstanceOfSatisfying(ResourceException.class,
                            e -> assertThat(e.getErrorCode()).isEqualTo("400"));
            assertThatThrownBy(() -> service.uploadResource(null)).isInstanceOf(ResourceException.class);

            verifyNoInteractions(fileStorage, resourcesRepository, eventPublisher);
        }

    }

    @Nested
    @DisplayName("Download")
    class Download {

        @Test
        @DisplayName("The file is read from storage by the saved location")
        void readsFileByLocation() {
            when(resourcesRepository.findById(3L)).thenReturn(Optional.of(resource(3L, "a.mp3")));
            when(fileStorage.download(LOCATION)).thenReturn(FILE);

            var result = service.getResource(3L);

            assertThat(result.getData()).isEqualTo(FILE);
            assertThat(result.getSize()).isEqualTo(FILE.length);
        }

        @Test
        @DisplayName("An unknown id gives ResourceNotFoundException (404) and storage is not called")
        void unknownIdIsNotFound() {
            when(resourcesRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getResource(99L))
                    .isInstanceOfSatisfying(ResourceNotFoundException.class, e -> {
                        assertThat(e.getErrorCode()).isEqualTo("404");
                        assertThat(e.getMessage()).contains("99");
                    });
            verifyNoInteractions(fileStorage);
        }

    }

    @Nested
    @DisplayName("Delete")
    class Delete {

        @Test
        @DisplayName("Existing resources are removed from storage, then from the database, then from song-service")
        void deletesFileThenRecordThenMetadata() {
            var first = resource(1L, "a.mp3");
            var second = resource(2L, "b.mp3");
            when(resourcesRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));

            var result = service.deleteResources("1,2");

            assertThat(result.getIds()).containsExactly(1L, 2L);
            var order = inOrder(fileStorage, resourcesRepository, songServiceClientProxy);
            order.verify(fileStorage).delete(StorageLocation.builder().bucket("resources").key("a.mp3").build());
            order.verify(fileStorage).delete(StorageLocation.builder().bucket("resources").key("b.mp3").build());
            order.verify(resourcesRepository).deleteAllInBatch(List.of(first, second));
            order.verify(songServiceClientProxy).delete(List.of(1L, 2L));
        }

        @Test
        @DisplayName("Unknown ids are ignored: only existing ones are deleted and reported")
        void unknownIdsAreIgnored() {
            var existing = resource(2L, "b.mp3");
            when(resourcesRepository.findAllById(List.of(1L, 2L, 3L))).thenReturn(List.of(existing));

            var result = service.deleteResources("1,2,3");

            assertThat(result.getIds()).containsExactly(2L);
            verify(songServiceClientProxy).delete(List.of(2L));
        }

        @Test
        @DisplayName("If no resource exists, storage, database and song-service are not touched")
        void nothingExistsNothingIsDeleted() {
            when(resourcesRepository.findAllById(List.of(5L))).thenReturn(List.of());

            var result = service.deleteResources("5");

            assertThat(result.getIds()).isEmpty();
            verify(resourcesRepository, never()).deleteAllInBatch(any());
            verifyNoInteractions(fileStorage, songServiceClientProxy);
        }

        @Test
        @DisplayName("An invalid id list is rejected before the database is used")
        void invalidIdsAreRejected() {
            assertThatThrownBy(() -> service.deleteResources("1,abc")).isInstanceOf(InvalidIdException.class);

            verifyNoMoreInteractions(resourcesRepository);
            verifyNoInteractions(fileStorage, songServiceClientProxy);
        }

        @Test
        @DisplayName("Deleting does not use the message broker")
        void brokerIsNotUsed() {
            when(resourcesRepository.findAllById(List.of(1L))).thenReturn(List.of(resource(1L, "a.mp3")));

            service.deleteResources("1");

            verifyNoInteractions(eventPublisher);
        }

    }

    private static AudioResource resource(Long id, String key) {
        return AudioResource.builder()
                .id(id)
                .storageBucket("resources")
                .storageKey(key)
                .fileSize((long) FILE.length)
                .build();
    }

}

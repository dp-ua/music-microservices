package com.music.processor.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.processor.client.dto.SongMetadataDto;
import com.music.processor.client.resource.ResourceServiceClientProxy;
import com.music.processor.client.song.SongServiceClientProxy;
import com.music.processor.exception.MetadataExtractionException;
import com.music.processor.exception.RemoteServiceException;
import com.music.processor.metadata.Mp3Metadata;
import com.music.processor.metadata.Mp3MetadataExtractor;

@ExtendWith(MockitoExtension.class)
class ResourceProcessingServiceImplTest {

    private static final Long RESOURCE_ID = 42L;
    private static final byte[] FILE_DATA = {1, 2, 3};

    @Mock
    private ResourceServiceClientProxy resourceServiceClient;
    @Mock
    private Mp3MetadataExtractor metadataExtractor;
    @Mock
    private SongServiceClientProxy songServiceClient;
    @InjectMocks
    private ResourceProcessingServiceImpl service;

    @Test
    @DisplayName("The file is downloaded, then metadata is extracted, then saved: in this order")
    void downloadsExtractsAndSavesInOrder() {
        when(resourceServiceClient.download(RESOURCE_ID)).thenReturn(FILE_DATA);
        when(metadataExtractor.extract(FILE_DATA)).thenReturn(fullMetadata());

        service.process(RESOURCE_ID);

        var order = inOrder(resourceServiceClient, metadataExtractor, songServiceClient);
        order.verify(resourceServiceClient).download(RESOURCE_ID);
        order.verify(metadataExtractor).extract(FILE_DATA);
        order.verify(songServiceClient).saveMetadata(any(SongMetadataDto.class));
    }

    @Test
    @DisplayName("The song id equals the resource id and all fields are passed on")
    void songIdEqualsResourceId() {
        when(resourceServiceClient.download(RESOURCE_ID)).thenReturn(FILE_DATA);
        when(metadataExtractor.extract(FILE_DATA)).thenReturn(fullMetadata());

        service.process(RESOURCE_ID);

        var saved = captureSavedMetadata();
        assertThat(saved.getId()).isEqualTo(RESOURCE_ID);
        assertThat(saved.getName()).isEqualTo("Test Title");
        assertThat(saved.getArtist()).isEqualTo("Test Artist");
        assertThat(saved.getAlbum()).isEqualTo("Test Album");
        assertThat(saved.getDuration()).isEqualTo("00:08");
        assertThat(saved.getYear()).isEqualTo("2025");
    }

    @Test
    @DisplayName("Missing tags are sent as they are, no default values are filled in")
    void missingFieldsAreNotFilledIn() {
        var withoutTags = Mp3Metadata.builder().duration("00:08").build();
        when(resourceServiceClient.download(RESOURCE_ID)).thenReturn(FILE_DATA);
        when(metadataExtractor.extract(FILE_DATA)).thenReturn(withoutTags);

        service.process(RESOURCE_ID);

        var saved = captureSavedMetadata();
        assertThat(saved.getName()).isNull();
        assertThat(saved.getArtist()).isNull();
        assertThat(saved.getAlbum()).isNull();
        assertThat(saved.getYear()).isNull();
        assertThat(saved.getDuration()).isEqualTo("00:08");
    }

    @Test
    @DisplayName("If the file can't be downloaded, nothing is extracted and nothing is saved")
    void downloadFailureStopsProcessing() {
        var failure = RemoteServiceException.emptyResponse("resource-service", "download resource 42");
        when(resourceServiceClient.download(RESOURCE_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.process(RESOURCE_ID)).isSameAs(failure);

        verifyNoInteractions(metadataExtractor, songServiceClient);
    }

    @Test
    @DisplayName("If the metadata can't be extracted, nothing is saved")
    void extractionFailureStopsProcessing() {
        var failure = new MetadataExtractionException(new RuntimeException("broken mp3"));
        when(resourceServiceClient.download(RESOURCE_ID)).thenReturn(FILE_DATA);
        when(metadataExtractor.extract(FILE_DATA)).thenThrow(failure);

        assertThatThrownBy(() -> service.process(RESOURCE_ID)).isSameAs(failure);

        verify(songServiceClient, never()).saveMetadata(any());
    }

    @Test
    @DisplayName("A failure of song-service is passed to the caller (the listener logs it and acknowledges)")
    void saveFailureIsPropagated() {
        var failure = RemoteServiceException.failed("song-service", "save metadata 42", 400, "Validation error", null);
        when(resourceServiceClient.download(RESOURCE_ID)).thenReturn(FILE_DATA);
        when(metadataExtractor.extract(FILE_DATA)).thenReturn(fullMetadata());
        doThrow(failure).when(songServiceClient).saveMetadata(any());

        assertThatThrownBy(() -> service.process(RESOURCE_ID)).isSameAs(failure);
    }

    private SongMetadataDto captureSavedMetadata() {
        var captor = ArgumentCaptor.forClass(SongMetadataDto.class);
        verify(songServiceClient).saveMetadata(captor.capture());
        return captor.getValue();
    }

    private static Mp3Metadata fullMetadata() {
        return Mp3Metadata.builder()
                .name("Test Title")
                .artist("Test Artist")
                .album("Test Album")
                .duration("00:08")
                .year("2025")
                .build();
    }

}

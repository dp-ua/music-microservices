package com.music.song.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.music.song.advice.BaseExceptionHandler;
import com.music.song.advice.ErrorResponseFactory;
import com.music.song.controller.SongsController;
import com.music.song.controller.common.exception.ControllerAdvice;
import com.music.song.controller.dto.MetadataUploadedDto;
import com.music.song.controller.dto.SongsDeletedDto;
import com.music.song.exception.MetadataWithIdExistException;
import com.music.song.service.SongsService;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;

/**
 * song-service as a provider: every request of the consumers' pacts (resource-processor: POST /songs,
 * resource-service: DELETE /songs) is replayed against the real controller, validation and error handling.
 * The service layer is a mock: the contract is about the shape of the API, the business logic is covered by
 * the other test types. A provider state prepares the answer of the mock.
 */
@WebMvcTest(SongsController.class)
@Provider("song-service")
@PactFolder("../build/pacts-shared")
class SongServicePactVerificationTest {

    /**
     * Only the web layer of song-service: the controller, the error handling, no database, no Eureka.
     */
    @SpringBootConfiguration
    @Import({SongsController.class, ControllerAdvice.class, BaseExceptionHandler.class, ErrorResponseFactory.class})
    static class WebLayerConfiguration {

    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SongsService songsService;

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("song service accepts new metadata")
    void songServiceAcceptsNewMetadata() {
        when(songsService.uploadMetadata(any())).thenReturn(MetadataUploadedDto.builder().id(1L).build());
    }

    @State("metadata for resource 2 already exists")
    void metadataForResource2Exists() {
        when(songsService.uploadMetadata(any())).thenThrow(MetadataWithIdExistException.byId(2L));
    }

    @State("metadata of songs 1 and 2 exist")
    void metadataOfSongs1And2Exist() {
        when(songsService.deleteSongs(eq("1,2"))).thenReturn(SongsDeletedDto.builder().ids(List.of(1L, 2L)).build());
    }

}

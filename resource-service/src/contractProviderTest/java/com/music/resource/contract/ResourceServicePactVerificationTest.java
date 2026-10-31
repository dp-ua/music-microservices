package com.music.resource.contract;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.music.resource.advice.BaseExceptionHandler;
import com.music.resource.advice.ErrorResponseFactory;
import com.music.resource.controller.ResourcesController;
import com.music.resource.controller.common.exception.ControllerAdvice;
import com.music.resource.exception.ResourceNotFoundException;
import com.music.resource.model.ResourceFile;
import com.music.resource.service.ResourcesService;

import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import au.com.dius.pact.provider.spring.junit5.MockMvcTestTarget;
import au.com.dius.pact.provider.spring.junit5.PactVerificationSpringProvider;

/**
 * resource-service as an HTTP provider: the requests of resource-processor (GET /resources/{id}) are replayed
 * against the real controller and error handling. The service layer is a mock, a provider state prepares its answer.
 */
@WebMvcTest(ResourcesController.class)
@Provider("resource-service")
@PactFolder("../build/pacts-shared")
class ResourceServicePactVerificationTest {

    /**
     * The content that the consumer expects in the contract for "resource 1 exists".
     */
    private static final byte[] MP3 = "ID3-fake-mp3-content".getBytes(UTF_8);

    /**
     * Only the web layer of resource-service: the controller, the error handling, no database, S3, Feign or Eureka.
     */
    @SpringBootConfiguration
    @Import({ResourcesController.class, ControllerAdvice.class, BaseExceptionHandler.class,
            ErrorResponseFactory.class})
    static class WebLayerConfiguration {

    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResourcesService resourcesService;

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new MockMvcTestTarget(mockMvc));
    }

    @TestTemplate
    @ExtendWith(PactVerificationSpringProvider.class)
    void verifyPact(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("resource 1 exists")
    void resource1Exists() {
        when(resourcesService.getResource(eq(1L)))
                .thenReturn(ResourceFile.builder().data(MP3).size((long) MP3.length).build());
    }

    @State("resource 999 does not exist")
    void resource999DoesNotExist() {
        when(resourcesService.getResource(eq(999L))).thenThrow(ResourceNotFoundException.byId(999L));
    }

}

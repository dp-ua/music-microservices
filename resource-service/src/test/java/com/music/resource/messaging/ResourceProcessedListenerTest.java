package com.music.resource.messaging;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.music.resource.service.ResourcesService;

@ExtendWith(MockitoExtension.class)
class ResourceProcessedListenerTest {

    @Mock
    private ResourcesService resourcesService;
    @InjectMocks
    private ResourceProcessedListener listener;

    @Test
    @DisplayName("The event moves the resource to PERMANENT")
    void marksResourceProcessed() {
        listener.onResourceProcessed(new ResourceProcessedEvent(5L));

        verify(resourcesService).markProcessed(5L);
    }

    @Test
    @DisplayName("An event without resourceId is ignored")
    void eventWithoutIdIsIgnored() {
        listener.onResourceProcessed(new ResourceProcessedEvent(null));
        listener.onResourceProcessed(null);

        verifyNoInteractions(resourcesService);
    }

}

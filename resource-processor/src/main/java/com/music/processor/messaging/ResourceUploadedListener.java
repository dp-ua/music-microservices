package com.music.processor.messaging;

import static org.apache.commons.lang3.exception.ExceptionUtils.getRootCauseMessage;

import java.util.Objects;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.music.processor.exception.MetadataExtractionException;
import com.music.processor.exception.RemoteServiceException;
import com.music.processor.service.ResourceProcessingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResourceUploadedListener {

    private final ResourceProcessingService processingService;

    /**
     * Any failure is logged and the message is acknowledged: the sync calls are already retried inside
     * and there is no dead-letter queue, so a requeued message would be processed in a loop.
     */
    @RabbitListener(queues = "${messaging.resource-uploaded.queue}")
    public void onResourceUploaded(ResourceUploadedEvent event) {
        log.info("Received message: {}", event);
        if (Objects.isNull(event) || Objects.isNull(event.resourceId())) {
            log.error("Message without resourceId is ignored: {}", event);
            return;
        }

        var resourceId = event.resourceId();
        try {
            processingService.process(resourceId);
        } catch (RemoteServiceException e) {
            log.error("Resource {} was NOT processed, metadata is not saved. {}", resourceId, e.getMessage());
        } catch (MetadataExtractionException e) {
            log.error("Resource {} was NOT processed, metadata is not saved. {}: {}",
                    resourceId, e.getMessage(), getRootCauseMessage(e));
        } catch (Exception e) {
            log.error("Resource {} was NOT processed because of an unexpected error", resourceId, e);
        }
    }

}

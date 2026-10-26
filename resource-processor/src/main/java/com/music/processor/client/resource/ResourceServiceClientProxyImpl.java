package com.music.processor.client.resource;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.music.processor.client.RemoteCallErrors;
import com.music.processor.exception.RemoteServiceException;
import com.music.processor.exception.TransientRemoteServiceException;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceServiceClientProxyImpl implements ResourceServiceClientProxy {

    private static final String SERVICE = "resource-service";
    private static final String OPERATION = "download resource";

    private final ResourceServiceClient resourceServiceClient;

    @Override
    @Retryable(
            retryFor = TransientRemoteServiceException.class,
            maxAttemptsExpression = "${processor.retry.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${processor.retry.delay-ms}",
                    multiplierExpression = "${processor.retry.multiplier}"))
    public byte[] download(Long resourceId) {
        log.info("Downloading resource {} from {}", resourceId, SERVICE);
        try {
            var data = resourceServiceClient.download(resourceId);
            if (isEmpty(data)) {
                throw RemoteServiceException.emptyResponse(SERVICE, OPERATION + " " + resourceId);
            }
            log.info("Resource {} downloaded, size: {} bytes", resourceId, data.length);
            return data;
        } catch (FeignException e) {
            var error = RemoteCallErrors.translate(SERVICE, OPERATION + " " + resourceId, e);
            if (error instanceof TransientRemoteServiceException) {
                log.warn("Attempt failed: {}", error.getMessage());
            }
            throw error;
        }
    }

}

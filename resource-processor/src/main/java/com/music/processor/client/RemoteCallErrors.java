package com.music.processor.client;

import static org.apache.commons.lang3.StringUtils.abbreviate;

import com.music.processor.exception.RemoteServiceException;
import com.music.processor.exception.TransientRemoteServiceException;

import feign.FeignException;

/**
 * Connection errors (status -1) and 5xx are transient and retried, everything else (4xx) is not.
 */
public final class RemoteCallErrors {

    private static final int MAX_BODY_LENGTH = 500;

    private RemoteCallErrors() {
    }

    public static RemoteServiceException translate(String service, String operation, FeignException e) {
        var status = e.status();
        var body = abbreviate(e.contentUTF8(), MAX_BODY_LENGTH);

        if (status <= 0 || status >= 500) {
            return TransientRemoteServiceException.unavailable(service, operation, status, body, e);
        }
        return RemoteServiceException.failed(service, operation, status, body, e);
    }

}

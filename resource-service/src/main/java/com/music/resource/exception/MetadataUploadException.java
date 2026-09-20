package com.music.resource.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class MetadataUploadException extends BaseServiceException {

    private static final String UNAVAILABLE =
            "song-service is unavailable for resourceId=%d";
    private static final String UPSTREAM_ERROR =
            "song-service returned an error for resourceId=%d";

    private MetadataUploadException(String message, String code, Throwable cause) {
        super(ServiceType.RESOURCE_SERVICE, code, message, cause);
    }

    public static MetadataUploadException unavailable(Long resourceId, Throwable cause) {
        return new MetadataUploadException(UNAVAILABLE.formatted(resourceId), "503", cause);
    }

    public static MetadataUploadException upstreamError(Long resourceId, Throwable cause) {
        return new MetadataUploadException(UPSTREAM_ERROR.formatted(resourceId), "502", cause);
    }

}

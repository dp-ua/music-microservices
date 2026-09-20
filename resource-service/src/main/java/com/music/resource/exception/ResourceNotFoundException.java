package com.music.resource.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class ResourceNotFoundException extends BaseServiceException {

    private static final String RESOURCE_NOT_FOUND_BY_ID = "Resource with ID=%d not found";

    private ResourceNotFoundException(String message) {
        super(ServiceType.RESOURCE_SERVICE, "404", message);
    }

    public static ResourceNotFoundException byId(Long id) {
        return new ResourceNotFoundException(RESOURCE_NOT_FOUND_BY_ID.formatted(id));
    }

}

package com.music.song.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class ResourceNotFoundException extends BaseServiceException {

    private static final String RESOURCE_NOT_FOUND = "Resource with ID %d not found";

    private ResourceNotFoundException(String message) {
        super(ServiceType.SONG_SERVICE, "404", message);
    }

    public static ResourceNotFoundException byId(Long id) {
        return new ResourceNotFoundException(RESOURCE_NOT_FOUND.formatted(id));
    }

}

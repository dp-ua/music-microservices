package com.music.resource.exception;

public class ResourceNotFoundException extends RuntimeException {

    private static final String RESOURCE_NOT_FOUND = "Resource with ID %d not found";

    private ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException byId(Long id) {
        return new ResourceNotFoundException(RESOURCE_NOT_FOUND.formatted(id));
    }

}

package com.music.storage.exception;

public class StorageAlreadyExistsException extends BaseServiceException {

    private static final String MESSAGE = "Storage with bucket '%s' and path '%s' already exists";

    private StorageAlreadyExistsException(String message) {
        super("400", message);
    }

    public static StorageAlreadyExistsException of(String bucket, String path) {
        return new StorageAlreadyExistsException(MESSAGE.formatted(bucket, path));
    }

}

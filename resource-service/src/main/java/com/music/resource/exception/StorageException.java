package com.music.resource.exception;

public class StorageException extends BaseServiceException {

    private static final String FAILED = "Cloud storage error: cannot %s file '%s'";
    private static final String NOT_FOUND = "File '%s' not found in cloud storage";

    private StorageException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }

    public static StorageException failed(String operation, String key, Throwable cause) {
        return new StorageException("500", FAILED.formatted(operation, key), cause);
    }

    public static StorageException notFound(String key, Throwable cause) {
        return new StorageException("404", NOT_FOUND.formatted(key), cause);
    }

}

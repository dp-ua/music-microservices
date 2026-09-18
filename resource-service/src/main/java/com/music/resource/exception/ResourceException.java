package com.music.resource.exception;

public class ResourceException extends RuntimeException {

    private static final String CAN_T_READ_FILE_DATA = "Can't read file data ";
    private static final String FILE_IS_EMPTY = "File{%s} is empty and cannot be saved";

    private ResourceException(String message) {
        super(message);
    }

    private ResourceException(String message, Throwable cause) {
        super(message, cause);
    }

    public static ResourceException canTReadFileData(String fileName, Throwable cause) {
        return new ResourceException(CAN_T_READ_FILE_DATA + fileName, cause);
    }

    public static ResourceException fileIsEmpty(String fileName) {
        return new ResourceException(FILE_IS_EMPTY.formatted(fileName));
    }

}

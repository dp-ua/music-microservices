package com.music.resource.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class ResourceException extends BaseServiceException {

    private static final String CAN_T_READ_FILE_DATA = "Can't read file data ";
    private static final String FILE_IS_EMPTY = "File{%s} is empty and cannot be saved";

    private ResourceException(String message, Throwable cause) {
        super(ServiceType.RESOURCE_SERVICE, "400", message, cause);
    }

    private ResourceException(String message) {
        super(ServiceType.RESOURCE_SERVICE, "400", message);
    }

    public static ResourceException canTReadFileData(String fileName, Throwable cause) {
        return new ResourceException(CAN_T_READ_FILE_DATA + fileName, cause);
    }

    public static ResourceException fileIsEmpty(String fileName) {
        return new ResourceException(FILE_IS_EMPTY.formatted(fileName));
    }

}

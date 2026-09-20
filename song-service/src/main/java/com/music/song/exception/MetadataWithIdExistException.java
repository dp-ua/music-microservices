package com.music.song.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class MetadataWithIdExistException extends BaseServiceException {

    private static final String METADATA_WITH_THE_SAME_ID_ALREADY_EXISTS =
            "Metadata with the same ID:{%s} already exists";

    private MetadataWithIdExistException(String message) {
        super(ServiceType.SONG_SERVICE, "409", message);
    }

    public static MetadataWithIdExistException byId(Long id) {
        return new MetadataWithIdExistException(
                METADATA_WITH_THE_SAME_ID_ALREADY_EXISTS.formatted(id)
        );
    }

}

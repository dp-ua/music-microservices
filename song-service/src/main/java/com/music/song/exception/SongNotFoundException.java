package com.music.song.exception;

import com.music.common.enums.ServiceType;
import com.music.common.exception.BaseServiceException;

public class SongNotFoundException extends BaseServiceException {

    private static final String RESOURCE_NOT_FOUND = "Song metadata for ID=%d not found";

    private SongNotFoundException(String message) {
        super(ServiceType.SONG_SERVICE, "404", message);
    }

    public static SongNotFoundException byId(Long id) {
        return new SongNotFoundException(RESOURCE_NOT_FOUND.formatted(id));
    }

}

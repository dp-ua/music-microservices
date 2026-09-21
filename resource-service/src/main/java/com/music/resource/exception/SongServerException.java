package com.music.resource.exception;

import com.music.resource.client.song.SongOperation;

public class SongServerException extends BaseServiceException {

    private static final String UNAVAILABLE =
            "song-service is unavailable: cannot %s ids=%s";
    private static final String UPSTREAM_ERROR =
            "song-service returned an error: cannot %s ids=%s";

    private SongServerException(String message, String code, Throwable cause) {
        super(code, message, cause);
    }

    public static SongServerException unavailable(SongOperation op, String ids, Throwable cause) {
        return new SongServerException(
                UNAVAILABLE.formatted(op.getDescription(), ids), "503", cause);
    }

    public static SongServerException upstreamError(SongOperation op, String ids, Throwable cause) {
        return new SongServerException(
                UPSTREAM_ERROR.formatted(op.getDescription(), ids), "502", cause);
    }

}

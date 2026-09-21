package com.music.song.exception;

public class MetadataWithIdExistException extends BaseServiceException {

    private static final String METADATA_WITH_THE_SAME_ID_ALREADY_EXISTS =
            "Metadata for resource ID=%s already exists";

    private MetadataWithIdExistException(String message) {
        super("409", message);
    }

    public static MetadataWithIdExistException byId(Long id) {
        return new MetadataWithIdExistException(
                METADATA_WITH_THE_SAME_ID_ALREADY_EXISTS.formatted(id)
        );
    }

}

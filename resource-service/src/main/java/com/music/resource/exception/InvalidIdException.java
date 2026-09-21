package com.music.resource.exception;

public class InvalidIdException extends BaseServiceException {

    private static final String CSV_TOO_LONG =
            "CSV string is too long: received %d characters, maximum allowed is %d";
    private static final String INVALID_FORMAT =
            "Invalid ID format: '%s'. Only positive integers are allowed";
    private static final String IDS_CANNOT_BE_EMPTY = "IDs cannot be empty";

    private InvalidIdException(String message) {
        super("400", message);
    }

    public static InvalidIdException csvTooLong(int actual, int max) {
        return new InvalidIdException(CSV_TOO_LONG.formatted(actual, max));
    }

    public static InvalidIdException invalidFormat(String value) {
        return new InvalidIdException(INVALID_FORMAT.formatted(value));
    }

    public static InvalidIdException empty() {
        return new InvalidIdException(IDS_CANNOT_BE_EMPTY);
    }

}

package com.music.resource.client.song;

public enum SongOperation {
    UPLOAD("upload metadata for"),
    DELETE("delete metadata for");

    private final String description;

    SongOperation(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
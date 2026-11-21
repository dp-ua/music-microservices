package com.music.storage.model;

/**
 * Processing phase of a file. A storage of each type is the place where the files in this phase are kept.
 */
public enum StorageType {

    /** The file is in processing. */
    STAGING,
    /** The file is successfully processed. */
    PERMANENT

}

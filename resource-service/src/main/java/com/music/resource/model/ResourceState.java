package com.music.resource.model;

/**
 * Processing phase of a file. The place of the files of each state is described by the storage of the same type
 * in storage-service.
 */
public enum ResourceState {

    /** The file is in processing. */
    STAGING,
    /** The file is successfully processed. */
    PERMANENT

}

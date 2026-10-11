package com.music.resource.storage;

public interface FileStorage {

    /**
     * Stores the file under a newly generated key.
     *
     * @return location of the stored file
     */
    StorageLocation upload(byte[] data);

    /**
     * @throws com.music.resource.exception.StorageException if the file does not exist or the storage fails
     */
    byte[] download(StorageLocation location);

    /**
     * Deletes the file. Deleting a file that does not exist is not an error.
     */
    void delete(StorageLocation location);

}

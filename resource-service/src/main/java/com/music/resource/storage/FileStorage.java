package com.music.resource.storage;

public interface FileStorage {

    /**
     * Stores the file in the given bucket, in the given path (folder), under a newly generated key.
     *
     * @return location of the stored file
     */
    StorageLocation upload(String bucket, String path, byte[] data);

    /**
     * Copies the file to another bucket / path, the name of the file is kept. The source is not touched.
     *
     * @return location of the copy
     */
    StorageLocation copy(StorageLocation source, String bucket, String path);

    /**
     * @throws com.music.resource.exception.StorageException if the file does not exist or the storage fails
     */
    byte[] download(StorageLocation location);

    /**
     * Deletes the file. Deleting a file that does not exist is not an error.
     */
    void delete(StorageLocation location);

}

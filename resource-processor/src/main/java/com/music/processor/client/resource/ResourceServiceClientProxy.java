package com.music.processor.client.resource;

public interface ResourceServiceClientProxy {

    /**
     * Downloads the binary content of the resource. Transient failures are retried.
     *
     * @throws com.music.processor.exception.RemoteServiceException if the file can't be received
     */
    byte[] download(Long resourceId);

}

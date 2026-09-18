package com.music.resource.client.song;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;

public interface SongServiceClientProxy {

    void uploadMetadata(ClientMetadataUploadDto clientDto);

}
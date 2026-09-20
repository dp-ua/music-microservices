package com.music.resource.client.song;

import java.util.List;

import com.music.resource.client.song.dto.ClientMetadataUploadDto;

public interface SongServiceClientProxy {

    void uploadMetadata(ClientMetadataUploadDto clientDto);

    void delete(List<Long> existingIds);

}
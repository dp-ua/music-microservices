package com.music.resource.client.song;

import java.util.List;

public interface SongServiceClientProxy {

    void delete(List<Long> existingIds);

}
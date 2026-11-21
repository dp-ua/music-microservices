package com.music.storage.service;

import java.util.List;

import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.controller.dto.StorageCreatedDto;
import com.music.storage.controller.dto.StoragesDeletedDto;
import com.music.storage.model.StorageDto;

public interface StorageService {

    StorageCreatedDto createStorage(StorageCreateDto dto);

    List<StorageDto> getAllStorages();

    /** Not existing ids are ignored. */
    StoragesDeletedDto deleteStorages(String csvIds);

}

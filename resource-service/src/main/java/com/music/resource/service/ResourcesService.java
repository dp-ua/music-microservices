package com.music.resource.service;

import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.model.ResourceFile;

public interface ResourcesService {

    ResourceUploadedDto uploadResource(byte[] fileData);

    ResourceFile getResource(Long id);

    ResourcesDeletedDto deleteResources(String ids);

}

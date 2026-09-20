package com.music.resource.service;

import org.springframework.web.multipart.MultipartFile;

import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.model.AudioResource;

public interface ResourcesService {

    ResourceUploadedDto uploadResource(MultipartFile file);

    AudioResource getResource(Long id);

    ResourcesDeletedDto deleteResources(String ids);

}

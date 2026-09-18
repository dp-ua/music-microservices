package com.music.resource.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;

public interface ResourcesService {

    ResourceUploadedDto uploadResource(MultipartFile file);

    byte[] getResource(Long id);

    ResourcesDeletedDto deleteResources(List<Long> ids);

}

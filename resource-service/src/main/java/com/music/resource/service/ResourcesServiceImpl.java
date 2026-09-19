package com.music.resource.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.music.resource.client.song.SongServiceClientProxy;
import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;
import com.music.resource.mapper.MetadataMapper;
import com.music.resource.mapper.ResourceMapper;
import com.music.resource.model.AudioResource;
import com.music.resource.repository.ResourcesRepository;
import com.music.resource.utils.MetadataUtilParser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcesServiceImpl implements ResourcesService {

    private final SongServiceClientProxy songServiceClientProxy;
    private final MetadataMapper metadataMapper;
    private final ResourceMapper resourceMapper;
    private final ResourcesRepository resourcesRepository;

    @Override
    public ResourceUploadedDto uploadResource(MultipartFile file) {
        var resourceId = saveFileToRepository(file);
        var metadata = MetadataUtilParser.parseMetadata(file);
        var clientDto = metadataMapper.toClientDtoWithCorrelationId(metadata, resourceId);

        songServiceClientProxy.uploadMetadata(clientDto);

        log.info("Resource uploaded with id: {}", resourceId);
        return ResourceUploadedDto.builder()
                .id(resourceId)
                .build();
    }

    @Override
    public AudioResource getResource(Long id) {
        log.info("Getting resource with id {}", id);
        return resourcesRepository.findById(id)
                .filter(resource -> !resource.getIsDeleted())
                .orElseThrow(() -> ResourceNotFoundException.byId(id));
    }

    @Override
    public ResourcesDeletedDto deleteResources(List<Long> ids) {
        var existingIds = resourcesRepository.findAllByIdInAndNotDeleted(ids).stream()
                .map(AudioResource::getId)
                .toList();

        if (!existingIds.isEmpty()) {
            resourcesRepository.softDeleteByIdIn(existingIds);
        }
        log.info("delete resources: {}", existingIds);
        return ResourcesDeletedDto.builder()
                .ids(existingIds)
                .build();
    }

    private Long saveFileToRepository(MultipartFile file) {
        var fileName = file.getOriginalFilename();
        var fileSize = file.getSize();
        var contentType = file.getContentType();

        byte[] fileData;
        try {
            fileData = file.getBytes();
        } catch (Exception e) {
            throw ResourceException.canTReadFileData(fileName, e);
        }

        if (fileData.length == 0) {
            throw ResourceException.fileIsEmpty(fileName);
        }

        return resourcesRepository
                .save(resourceMapper.toEntity(fileName, fileData, fileSize, contentType))
                .getId();
    }

}

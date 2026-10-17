package com.music.resource.service;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

import org.springframework.stereotype.Service;

import com.music.resource.client.song.SongServiceClientProxy;
import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;
import com.music.resource.mapper.ResourceMapper;
import com.music.resource.messaging.ResourceEventPublisher;
import com.music.resource.model.AudioResource;
import com.music.resource.model.ResourceFile;
import com.music.resource.repository.ResourcesRepository;
import com.music.resource.storage.FileStorage;
import com.music.resource.storage.StorageLocation;
import com.music.resource.validator.CSVValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourcesServiceImpl implements ResourcesService {

    private final SongServiceClientProxy songServiceClientProxy;
    private final ResourceMapper resourceMapper;
    private final ResourcesRepository resourcesRepository;
    private final FileStorage fileStorage;
    private final ResourceEventPublisher resourceEventPublisher;
    private final CSVValidator validator;

    @Override
    public ResourceUploadedDto uploadResource(byte[] fileData) {
        if (isEmpty(fileData)) {
            throw ResourceException.fileIsEmpty("unknown");
        }

        var location = fileStorage.upload(fileData);
        var resourceId = saveLocation(location, fileData.length);
        resourceEventPublisher.publishUploaded(resourceId);

        log.info("Resource uploaded with id: {}, location: {}", resourceId, location);
        return ResourceUploadedDto.builder()
                .id(resourceId)
                .build();
    }

    @Override
    public ResourceFile getResource(Long id) {
        log.info("Getting resource with id {}", id);
        var resource = resourcesRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.byId(id));

        var data = fileStorage.download(resourceMapper.toLocation(resource));
        return resourceMapper.toResourceFile(resource, data);
    }

    @Override
    public ResourcesDeletedDto deleteResources(String id) {
        var ids = validator.parseAndValidateIds(id);

        var resources = resourcesRepository.findAllById(ids);
        var existingIds = resources.stream()
                .map(AudioResource::getId)
                .toList();

        if (!existingIds.isEmpty()) {
            resources.forEach(resource -> fileStorage.delete(resourceMapper.toLocation(resource)));
            resourcesRepository.deleteAllInBatch(resources);
            songServiceClientProxy.delete(existingIds);
        }

        log.info("Resource deleted ids: {}", existingIds);
        return ResourcesDeletedDto.builder()
                .ids(existingIds)
                .build();
    }

    private Long saveLocation(StorageLocation location, long fileSize) {
        try {
            return resourcesRepository
                    .save(resourceMapper.toEntity(location, fileSize))
                    .getId();
        } catch (RuntimeException e) {
            log.error("Can't save resource location {}, removing the uploaded file", location, e);
            compensateUpload(location, e);
            throw e;
        }
    }

    private void compensateUpload(StorageLocation location, RuntimeException cause) {
        try {
            fileStorage.delete(location);
        } catch (RuntimeException cleanupError) {
            cause.addSuppressed(cleanupError);
        }
    }

}

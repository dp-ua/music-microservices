package com.music.resource.service;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.music.resource.client.song.SongServiceClientProxy;
import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.exception.ResourceException;
import com.music.resource.exception.ResourceNotFoundException;
import com.music.resource.client.storage.StorageRegistry;
import com.music.resource.client.storage.StorageType;
import com.music.resource.mapper.ResourceMapper;
import com.music.resource.messaging.ResourceEventPublisher;
import com.music.resource.model.AudioResource;
import com.music.resource.model.ResourceFile;
import com.music.resource.model.ResourceState;
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
    private final StorageRegistry storageRegistry;

    @Override
    public ResourceUploadedDto uploadResource(byte[] fileData) {
        if (isEmpty(fileData)) {
            throw ResourceException.fileIsEmpty("unknown");
        }

        var staging = storageRegistry.get(StorageType.STAGING);
        var location = fileStorage.upload(staging.getBucket(), staging.getPath(), fileData);
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

    @Override
    public void markProcessed(Long id) {
        var resource = resourcesRepository.findById(id).orElse(null);
        if (Objects.isNull(resource)) {
            log.warn("Resource {} is processed, but it does not exist (deleted?), nothing to move", id);
            return;
        }
        if (resource.getState() == ResourceState.PERMANENT) {
            log.info("Resource {} is already PERMANENT, nothing to do", id);
            return;
        }

        var permanent = storageRegistry.get(StorageType.PERMANENT);
        var source = resourceMapper.toLocation(resource);
        // copy first, delete the source last: whatever fails, the file is not lost
        var target = fileStorage.copy(source, permanent.getBucket(), permanent.getPath());
        try {
            resource.setState(ResourceState.PERMANENT);
            resource.setStorageBucket(target.getBucket());
            resource.setStorageKey(target.getKey());
            resourcesRepository.save(resource);
        } catch (RuntimeException e) {
            log.error("Can't update resource {} after the move, removing the copy {}", id, target, e);
            compensateUpload(target, e);
            throw e;
        }
        deleteSourceQuietly(source);
        log.info("Resource {} moved to PERMANENT: {} -> {}", id, source, target);
    }

    private void deleteSourceQuietly(StorageLocation source) {
        try {
            fileStorage.delete(source);
        } catch (RuntimeException e) {
            log.warn("Resource is already PERMANENT, but the staging file {} was not deleted", source, e);
        }
    }

    private Long saveLocation(StorageLocation location, long fileSize) {
        try {
            return resourcesRepository
                    .save(resourceMapper.toEntity(location, fileSize, ResourceState.STAGING))
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

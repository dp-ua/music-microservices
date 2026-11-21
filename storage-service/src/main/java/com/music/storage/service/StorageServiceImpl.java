package com.music.storage.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.controller.dto.StorageCreatedDto;
import com.music.storage.controller.dto.StoragesDeletedDto;
import com.music.storage.exception.StorageAlreadyExistsException;
import com.music.storage.mapper.StorageMapper;
import com.music.storage.model.Storage;
import com.music.storage.model.StorageDto;
import com.music.storage.repository.StorageRepository;
import com.music.storage.validator.CSVValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageServiceImpl implements StorageService {

    private final StorageRepository repository;
    private final StorageMapper mapper;
    private final CSVValidator validator;

    @Override
    @Transactional
    public StorageCreatedDto createStorage(StorageCreateDto dto) {
        if (repository.existsByBucketAndPath(dto.getBucket(), dto.getPath())) {
            throw StorageAlreadyExistsException.of(dto.getBucket(), dto.getPath());
        }
        var saved = repository.save(mapper.toEntity(dto));
        log.info("Storage created: id={}, type={}, bucket={}, path={}",
                saved.getId(), saved.getStorageType(), saved.getBucket(), saved.getPath());
        return StorageCreatedDto.builder().id(saved.getId()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StorageDto> getAllStorages() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public StoragesDeletedDto deleteStorages(String csvIds) {
        var ids = validator.parseAndValidateIds(csvIds);
        var storages = repository.findAllById(ids);
        var deletedIds = storages.stream().map(Storage::getId).toList();
        repository.deleteAllInBatch(storages);
        log.info("Storages deleted ids: {}", deletedIds);
        return StoragesDeletedDto.builder().ids(deletedIds).build();
    }

}

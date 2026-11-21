package com.music.storage.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.storage.controller.api.StorageControllerApi;
import com.music.storage.controller.dto.StorageCreateDto;
import com.music.storage.controller.dto.StorageCreatedDto;
import com.music.storage.controller.dto.StoragesDeletedDto;
import com.music.storage.model.StorageDto;
import com.music.storage.service.StorageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/storages")
public class StorageController implements StorageControllerApi {

    private final StorageService storageService;

    @Override
    @PostMapping
    public ResponseEntity<StorageCreatedDto> createStorage(@RequestBody @Valid StorageCreateDto storage) {
        log.info("Creating storage: {}", storage);
        return ResponseEntity.ok(storageService.createStorage(storage));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<StorageDto>> getStorages() {
        return ResponseEntity.ok(storageService.getAllStorages());
    }

    @Override
    @DeleteMapping
    public ResponseEntity<StoragesDeletedDto> deleteStorages(@RequestParam String id) {
        log.info("Deleting storages with IDs: {}", id);
        return ResponseEntity.ok(storageService.deleteStorages(id));
    }

}

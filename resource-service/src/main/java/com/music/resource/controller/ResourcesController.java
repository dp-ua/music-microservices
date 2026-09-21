package com.music.resource.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.music.resource.controller.api.ResourcesControllerApi;
import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.service.ResourcesService;

import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/resources")
@Validated
@Slf4j
public class ResourcesController implements ResourcesControllerApi {

    private final ResourcesService resourcesService;

    @Override
    @PostMapping(consumes = "audio/mpeg")
    public ResponseEntity<ResourceUploadedDto> uploadResource(@RequestBody byte[] fileData) {
        log.info("Upload resource. size: {} bytes", fileData.length);
        return ResponseEntity.ok(resourcesService.uploadResource(fileData));
    }

    @Override
    @GetMapping(path = "/{id}", produces = "audio/mpeg")
    public ResponseEntity<byte[]> getResource(@PathVariable @Positive Long id) {
        var content = resourcesService.getResource(id);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .contentLength(content.getFileSize())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(content.getFileData());
    }

    @Override
    @DeleteMapping
    public ResponseEntity<ResourcesDeletedDto> deleteResources(@RequestParam String id) {
        log.info("Delete resources. id: [{}]", id);
        return ResponseEntity.ok(resourcesService.deleteResources(id));
    }

}
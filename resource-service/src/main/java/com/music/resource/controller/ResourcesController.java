package com.music.resource.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.music.resource.controller.api.ResourcesControllerApi;
import com.music.resource.controller.dto.ResourceUploadedDto;
import com.music.resource.controller.dto.ResourcesDeletedDto;
import com.music.resource.service.ResourcesService;
import com.music.resource.validation.ValidMp3File;

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
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResourceUploadedDto> uploadResource(@RequestParam("file") @ValidMp3File MultipartFile file) {
        log.info("Upload resource. originalFilename: {}, size: {} bytes, contentType: {}",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
        return ResponseEntity.ok(resourcesService.uploadResource(file));
    }

    @Override
    @GetMapping(path = "/{id}")
    public ResponseEntity<Resource> getResource(@PathVariable @Positive Long id) {
        var content = resourcesService.getResource(id);
        var resource = new ByteArrayResource(content.getFileData());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(content.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(content.getFileName()).build().toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    @Override
    @DeleteMapping
    public ResponseEntity<ResourcesDeletedDto> deleteResources(@RequestParam String id) {
        log.info("Delete resources. id: [{}]", id);
        return ResponseEntity.ok(resourcesService.deleteResources(id));
    }

}
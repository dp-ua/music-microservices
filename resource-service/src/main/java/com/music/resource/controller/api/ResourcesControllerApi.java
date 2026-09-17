package com.music.resource.controller.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Resource Controller Api")
public interface ResourceControllerApi {

    @Operation(summary = "Upload MP3 resource")
    @ApiResponse(responseCode = "201", description = "Resource uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    @PostMapping(path = "/resources", consumes = "audio/mpeg")
    ResponseEntity<Map<String, Long>> uploadResource(@RequestBody byte[] resourceData);

    @Operation(summary = "Get resource")
    @ApiResponse(responseCode = "200", description = "Resource retrieved successfully")
    @ApiResponse(responseCode = "400", description = "Bad request")
    @ApiResponse(responseCode = "404", description = "Resource not found")
    @GetMapping(path = "/resources/{id}", produces = "audio/mpeg")
    ResponseEntity<byte[]> getResource(@PathVariable Long id);

    @Operation(summary = "Delete resources")
    @ApiResponse(responseCode = "200", description = "Request successful, resources deleted as specified")
    @ApiResponse(responseCode = "400", description = "Bad request")
    @DeleteMapping("/resources")
    ResponseEntity<Map<String, List<Long>>> deleteResources(@RequestParam String ids);

}

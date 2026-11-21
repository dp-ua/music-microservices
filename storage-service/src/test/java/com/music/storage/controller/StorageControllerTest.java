package com.music.storage.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.music.storage.advice.ErrorResponseFactory;
import com.music.storage.controller.common.exception.ControllerAdvice;
import com.music.storage.controller.dto.StorageCreatedDto;
import com.music.storage.controller.dto.StoragesDeletedDto;
import com.music.storage.exception.InvalidIdException;
import com.music.storage.exception.StorageAlreadyExistsException;
import com.music.storage.model.StorageDto;
import com.music.storage.model.StorageType;
import com.music.storage.service.StorageService;

/**
 * HTTP contract of the Storage API: status codes, JSON bodies, error format. The service is a mock.
 */
@WebMvcTest(StorageController.class)
class StorageControllerTest {

    /** Only the web layer: the controller and the error handling, no database, no Eureka. */
    @SpringBootConfiguration
    @Import({StorageController.class, ControllerAdvice.class, ErrorResponseFactory.class})
    static class WebLayerConfiguration {

    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorageService storageService;

    @Test
    @DisplayName("POST /storages: 200 and {\"id\": N}")
    void createReturnsId() throws Exception {
        when(storageService.createStorage(any())).thenReturn(new StorageCreatedDto(1L));

        mockMvc.perform(post("/storages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"PERMANENT\",\"bucket\":\"bucket_name\",\"path\":\"/files\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    @DisplayName("POST /storages: a body without bucket is 400 with the field in details")
    void createWithoutBucketIsBadRequest() throws Exception {
        mockMvc.perform(post("/storages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"STAGING\",\"path\":\"/files\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.errorCode").value("400"))
                .andExpect(jsonPath("$.details.bucket").exists());
        verify(storageService, never()).createStorage(any());
    }

    @Test
    @DisplayName("POST /storages: a storageType that is not STAGING / PERMANENT is 400")
    void createWithUnknownTypeIsBadRequest() throws Exception {
        mockMvc.perform(post("/storages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"TEMP\",\"bucket\":\"b\",\"path\":\"/files\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

    @Test
    @DisplayName("POST /storages: a duplicate is 400")
    void createDuplicateIsBadRequest() throws Exception {
        when(storageService.createStorage(any())).thenThrow(StorageAlreadyExistsException.of("b", "/files"));

        mockMvc.perform(post("/storages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"STAGING\",\"bucket\":\"b\",\"path\":\"/files\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

    @Test
    @DisplayName("POST /storages: an unexpected error is 500 in the common error format")
    void unexpectedErrorIsServerError() throws Exception {
        when(storageService.createStorage(any())).thenThrow(new IllegalStateException("db is down"));

        mockMvc.perform(post("/storages").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"storageType\":\"STAGING\",\"bucket\":\"b\",\"path\":\"/files\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("500"));
    }

    @Test
    @DisplayName("GET /storages: 200 and the list of id, storageType, bucket, path")
    void getReturnsList() throws Exception {
        when(storageService.getAllStorages())
                .thenReturn(List.of(new StorageDto(1L, StorageType.PERMANENT, "bucket_name", "/files")));

        mockMvc.perform(get("/storages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].storageType").value("PERMANENT"))
                .andExpect(jsonPath("$[0].bucket").value("bucket_name"))
                .andExpect(jsonPath("$[0].path").value("/files"));
    }

    @Test
    @DisplayName("DELETE /storages?id=1,2: 200 and the deleted ids")
    void deleteReturnsIds() throws Exception {
        when(storageService.deleteStorages("1,2")).thenReturn(new StoragesDeletedDto(List.of(1L, 2L)));

        mockMvc.perform(delete("/storages").param("id", "1,2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ids[0]").value(1))
                .andExpect(jsonPath("$.ids[1]").value(2));
    }

    @Test
    @DisplayName("DELETE /storages: an invalid CSV is 400")
    void deleteInvalidCsvIsBadRequest() throws Exception {
        when(storageService.deleteStorages("a,b")).thenThrow(InvalidIdException.invalidFormat("a"));

        mockMvc.perform(delete("/storages").param("id", "a,b"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("400"));
    }

}

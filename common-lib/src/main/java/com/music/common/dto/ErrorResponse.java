package com.music.common.dto;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.music.common.enums.ServiceType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private String errorMessage;
    private String errorCode;
    private Map<String, String> details;
    private ServiceType service;
}

package com.music.resource.controller.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ResourcesDeletedDto {

    private List<Long> ids;

}

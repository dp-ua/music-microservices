package com.music.resource.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.music.resource.controller.api.ResourceControllerApi;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/resources")
@Validated
public class ResourceController implements ResourceControllerApi {

}
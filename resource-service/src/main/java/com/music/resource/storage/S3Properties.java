package com.music.resource.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "storage.s3")
public class S3Properties {

    private String endpoint;
    private String region;
    private String bucket;
    private String accessKey;
    private String secretKey;
    private boolean pathStyleAccess;

}
